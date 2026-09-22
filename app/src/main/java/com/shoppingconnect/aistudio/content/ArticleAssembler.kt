package com.shoppingconnect.aistudio.content

import com.shoppingconnect.aistudio.ai.WriterDraft
import com.shoppingconnect.aistudio.core.common.formatDateTime
import com.shoppingconnect.aistudio.core.common.formatPrice
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.Block
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.SourceRef
import com.shoppingconnect.aistudio.domain.model.UsageStatus

/**
 * Turns the writer's draft into editor blocks and adds everything that must NOT come from the
 * model: the verified product facts summary, the affiliate link, CTA, disclosure and price notice.
 */
object ArticleAssembler {

    fun assemble(draft: WriterDraft, product: Product, strategy: ContentStrategy, settings: AppSettings): Article {
        val blocks = mutableListOf<Block>()
        // Disclosure at the top as well as the bottom, so it is visible before the link.
        if (settings.disclosureText.isNotBlank()) blocks += Block(type = BlockType.DISCLOSURE, text = settings.disclosureText, aiGenerated = false)
        if (draft.hook.isNotBlank()) blocks += Block(type = BlockType.PARAGRAPH, text = sanitize(draft.hook, strategy.usage))
        draft.sections.forEach { s ->
            when (s.type.lowercase()) {
                "h2" -> blocks += Block(type = BlockType.H2, text = sanitize(s.text, strategy.usage).replace("**", ""))
                "list" -> if (s.items.isNotEmpty()) blocks += Block(type = BlockType.LIST, items = s.items.map { sanitize(it, strategy.usage) })
                "quote" -> blocks += Block(type = BlockType.QUOTE, text = sanitize(s.text, strategy.usage))
                else -> KoreanText.splitParagraph(sanitize(s.text, strategy.usage)).forEach { blocks += Block(type = BlockType.PARAGRAPH, text = it) }
            }
        }
        blocks += Block(type = BlockType.DIVIDER, aiGenerated = false)
        blocks += productCard(product, settings)
        blocks += Block(type = BlockType.LINK, text = "상품 자세히 보기", url = product.affiliateUrl, aiGenerated = false)
        val cta = strategy.cta.ifBlank { settings.brand.defaultCta }
        if (cta.isNotBlank()) blocks += Block(type = BlockType.CTA, text = cta, url = product.affiliateUrl, aiGenerated = false)
        if (settings.brand.signature.isNotBlank()) blocks += Block(type = BlockType.PARAGRAPH, text = settings.brand.signature, aiGenerated = false)
        if (settings.disclosureText.isNotBlank()) blocks += Block(type = BlockType.DISCLOSURE, text = settings.disclosureText, aiGenerated = false)

        val tags = (draft.hashtags + settings.brand.defaultHashtags)
            .map { it.trim().removePrefix("#").replace(" ", "") }.filter { it.isNotBlank() }.distinct().take(15)
        return Article(
            title = "",
            blocks = blocks,
            hashtags = tags,
            disclosure = settings.disclosureText,
            factsCheckedAt = product.extractionTimestamp,
            sources = listOf(SourceRef(product.source.label, product.sourceUrl, product.extractionTimestamp)),
        )
    }

    /** Guard rail even if the model slipped: neutralise first-hand-use claims for non-users. */
    private fun sanitize(text: String, usage: UsageStatus): String =
        if (usage == UsageStatus.USED) text.trim() else ComplianceChecker.neutralize(text.trim())

    /** Product facts panel built only from verified source data. */
    fun productCard(product: Product, settings: AppSettings): Block {
        val rows = buildList {
            add("상품명: ${product.title}")
            if (product.isVerified(Product.F_BRAND)) add("브랜드: ${product.brand}")
            if (product.isVerified(Product.F_PRICE)) formatPrice(product.price, product.currency)?.let {
                add("판매가: $it (${product.extractionTimestamp.formatDateTime()} 확인)")
            }
            if (product.isVerified(Product.F_ORIGINAL_PRICE)) formatPrice(product.originalPrice, product.currency)?.let { add("정가: $it") }
            if (product.isVerified(Product.F_SELLER)) add("판매처: ${product.seller}")
            product.specifications.take(6).forEach { add("${it.name}: ${it.value}") }
            if (settings.priceChangeNotice && product.isVerified(Product.F_PRICE)) add(AppSettings.PRICE_CHANGE_NOTICE)
        }
        return Block(type = BlockType.PRODUCT_CARD, text = "제품 정보 요약", items = rows, url = product.affiliateUrl, aiGenerated = false)
    }
}
