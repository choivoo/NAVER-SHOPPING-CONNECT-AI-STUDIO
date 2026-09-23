package com.shoppingconnect.aistudio.visual

import com.shoppingconnect.aistudio.ai.VisualDraft
import com.shoppingconnect.aistudio.core.common.formatPrice
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.AspectRatio
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.Block
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.BlogCard
import com.shoppingconnect.aistudio.domain.model.CardStyle
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.CardType
import com.shoppingconnect.aistudio.domain.model.CopyrightType
import com.shoppingconnect.aistudio.domain.model.ExtractedPalette
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.Spec
import com.shoppingconnect.aistudio.domain.model.VisualPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** AI Blog Visual Design Engine: plan → cards (verified data only) → rendered assets → placed in the article. */
@Singleton
class VisualService @Inject constructor(private val media: MediaRepository) {

    /** SPEC rows come exclusively from verified product data — never from the model. */
    fun specRows(product: Product): List<Spec> = buildList {
        if (product.isVerified(Product.F_BRAND)) add(Spec("브랜드", product.brand!!))
        if (product.isVerified(Product.F_PRICE)) formatPrice(product.price, product.currency)?.let { add(Spec("판매가", "$it (작성 시점)")) }
        if (product.isVerified(Product.F_CATEGORY)) add(Spec("카테고리", product.category!!.substringAfterLast(">").trim()))
        addAll(product.specifications.take(6))
        if (product.isVerified(Product.F_OPTIONS)) add(Spec("옵션", product.options.take(4).joinToString(", ")))
    }.take(8)

    fun buildCards(draft: VisualDraft, product: Product, images: List<MediaAssetEntity>, article: Article, style: CardStylePreset, brandText: String): List<BlogCard> {
        val h2 = article.blocks.filter { it.type == BlockType.H2 }
        return draft.cards.filter { it.type != CardType.COMPARISON }.map { d ->
            val aspect = when (d.type) { CardType.HERO -> AspectRatio.PORTRAIT_4_5; CardType.CTA -> AspectRatio.WIDE_16_9; CardType.SPEC -> AspectRatio.PORTRAIT_4_5; else -> AspectRatio.SQUARE }
            BlogCard(
                type = d.type,
                title = d.title.ifBlank { d.type.label }.take(40),
                subtitle = d.subtitle.take(60),
                items = d.items.map { it.take(60) }.take(5),
                rows = if (d.type == CardType.SPEC) specRows(product) else emptyList(),
                cta = d.cta.take(16),
                imageAssetId = images.getOrNull(d.imageIndex)?.id ?: if (d.type == CardType.HERO || d.type == CardType.CTA) images.firstOrNull()?.id else null,
                style = CardStyle(preset = style, aspect = aspect, brandText = brandText),
                afterBlockId = if (d.afterSection < 0) null else h2.getOrNull(d.afterSection)?.id,
            )
        }
    }

    suspend fun palette(imageAssetId: String?): ExtractedPalette? = withContext(Dispatchers.Default) {
        val bmp = media.loadBitmap(imageAssetId, 400) ?: return@withContext null
        ColorTools.extract(bmp).also { bmp.recycle() }
    }

    suspend fun renderCard(projectId: String, card: BlogCard, width: Int, palette: ExtractedPalette?): MediaAssetEntity = withContext(Dispatchers.Default) {
        val img = media.loadBitmap(card.imageAssetId, width)
        val bmp = CardRenderer.render(card, width, img, palette)
        img?.recycle()
        val meta = AppJson.encodeToString(BlogCard.serializer(), card)
        media.saveBitmap(projectId, bmp, AssetKind.BLOG, "card_${card.type.name.lowercase()}", meta, CopyrightType.APP_GENERATED, replaceId = card.renderedAssetId)
            .also { bmp.recycle() }
    }

    suspend fun renderAll(projectId: String, cards: List<BlogCard>, width: Int): List<BlogCard> {
        val pal = palette(cards.firstOrNull { it.type == CardType.HERO }?.imageAssetId ?: cards.firstNotNullOfOrNull { it.imageAssetId })
        return cards.map { c -> c.copy(renderedAssetId = renderCard(projectId, c, width, pal).id) }
    }

    /** Places card images in the article: hero after the intro, others after their section heading's content. */
    fun placeInArticle(article: Article, cards: List<BlogCard>): Article {
        val base = article.blocks.filterNot { it.type == BlockType.IMAGE && cards.any { c -> c.renderedAssetId == it.assetId } || (it.type == BlockType.IMAGE && it.url == CARD_MARK) }
        val out = mutableListOf<Block>()
        val hero = cards.firstOrNull { it.type == CardType.HERO && it.renderedAssetId != null }
        val bySection = cards.filter { it !== hero && it.renderedAssetId != null }.groupBy { it.afterBlockId }
        var heroPlaced = hero == null
        val h2Ids = base.filter { it.type == BlockType.H2 }.map { it.id }
        base.forEachIndexed { i, b ->
            // hero goes right before the first H2
            if (!heroPlaced && b.type == BlockType.H2) { out += imageBlock(hero!!); heroPlaced = true }
            out += b
            // section cards go after the last block of their section (before the next H2)
            val next = base.getOrNull(i + 1)
            val sectionOwner = base.subList(0, i + 1).lastOrNull { it.type == BlockType.H2 }?.id
            if (sectionOwner != null && (next == null || next.type == BlockType.H2 || next.type == BlockType.DIVIDER)) {
                bySection[sectionOwner]?.forEach { out += imageBlock(it) }
            }
        }
        if (!heroPlaced && hero != null) out.add(0, imageBlock(hero))
        // cards whose section vanished are appended before the product card
        val placed = out.mapNotNull { it.assetId }.toSet()
        val orphans = cards.filter { it.renderedAssetId != null && it.renderedAssetId !in placed && (it.afterBlockId == null || it.afterBlockId !in h2Ids) && it !== hero }
        if (orphans.isNotEmpty()) {
            val idx = out.indexOfFirst { it.type == BlockType.PRODUCT_CARD }.let { if (it < 0) out.size else it }
            out.addAll(idx, orphans.map { imageBlock(it) })
        }
        return article.copy(blocks = out)
    }

    private fun imageBlock(c: BlogCard) = Block(type = BlockType.IMAGE, assetId = c.renderedAssetId, text = c.type.label, url = CARD_MARK, aiGenerated = false)

    fun plan(draft: VisualDraft, cards: List<BlogCard>) = VisualPlan(draft.visualTheme, cards.firstOrNull()?.style?.preset ?: draft.stylePreset, cards)

    companion object { const val CARD_MARK = "card://generated" }
}
