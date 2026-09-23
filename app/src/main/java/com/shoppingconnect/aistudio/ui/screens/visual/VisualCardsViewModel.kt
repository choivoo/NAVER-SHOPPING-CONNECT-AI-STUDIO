package com.shoppingconnect.aistudio.ui.screens.visual

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.ai.AiEngineProvider
import com.shoppingconnect.aistudio.ai.DemoAiEngine
import com.shoppingconnect.aistudio.core.common.toAppException
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.data.repository.ProjectBundle
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.BlogCard
import com.shoppingconnect.aistudio.domain.model.CardStyle
import com.shoppingconnect.aistudio.domain.model.CardType
import com.shoppingconnect.aistudio.domain.model.VisualPlan
import com.shoppingconnect.aistudio.media.export.MediaExporter
import com.shoppingconnect.aistudio.visual.VisualService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Shared persistence for card edits: plan + rendered assets + article placement stay consistent. */
class CardStore(private val repo: ProjectRepository, private val visuals: VisualService, private val media: MediaRepository) {
    suspend fun commit(projectId: String, b: ProjectBundle, cards: List<BlogCard>, width: Int, rerender: Set<String>) {
        val pal = visuals.palette(cards.firstOrNull { it.type == CardType.HERO }?.imageAssetId ?: cards.firstNotNullOfOrNull { it.imageAssetId })
        val oldIds = b.visualPlan?.cards?.mapNotNull { it.renderedAssetId }.orEmpty().toSet()
        val rendered = cards.map { c ->
            if (c.id in rerender || c.renderedAssetId == null) {
                val asset = visuals.renderCard(projectId, c.copy(renderedAssetId = null), width, pal)
                c.copy(renderedAssetId = asset.id)
            } else c
        }
        val keep = rendered.mapNotNull { it.renderedAssetId }.toSet()
        oldIds.filterNot { it in keep }.forEach { id -> media.get(id)?.let { media.delete(it) } }
        val plan = (b.visualPlan ?: VisualPlan()).copy(cards = rendered)
        val article = b.article?.let { a ->
            // remove image blocks that pointed at removed/re-rendered cards, then re-place
            visuals.placeInArticle(a.copy(blocks = a.blocks.filterNot { it.type == BlockType.IMAGE && it.assetId in oldIds && it.assetId !in keep }), rendered)
        }
        if (article != null && b.strategy != null) repo.saveArticle(projectId, article, b.strategy, null, "", b.promptVersion ?: "v1", b.qualityScore, plan)
        else repo.saveVisualPlan(projectId, plan)
    }
}

@HiltViewModel
class VisualCardsViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ProjectRepository,
    private val visuals: VisualService,
    private val media: MediaRepository,
    private val engines: AiEngineProvider,
    private val settings: SettingsRepository,
    private val exporter: MediaExporter,
) : ViewModel() {
    val projectId: String = checkNotNull(handle["projectId"])
    val bundle = repo.observeBundle(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val store = CardStore(repo, visuals, media)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _msg = MutableStateFlow<String?>(null)
    val msg = _msg.asStateFlow()
    fun dismiss() { _msg.value = null }

    private fun edit(rerender: Set<String> = emptySet(), f: (List<BlogCard>) -> List<BlogCard>) = viewModelScope.launch {
        val b = repo.observeBundleOnce(projectId) ?: return@launch
        _busy.value = true
        try { store.commit(projectId, b, f(b.visualPlan?.cards.orEmpty()), settings.current().cardExportWidth, rerender) }
        catch (e: Exception) { _msg.value = e.toAppException().userMessage }
        _busy.value = false
    }

    fun delete(id: String) = edit { it.filterNot { c -> c.id == id } }
    fun move(id: String, d: Int) = edit { list ->
        val m = list.toMutableList(); val i = m.indexOfFirst { it.id == id }; val j = i + d
        if (i >= 0 && j in m.indices) { val c = m.removeAt(i); m.add(j, c) }
        m
    }
    fun regenerate(id: String) = edit(setOf(id)) { it }

    fun add(type: CardType) = viewModelScope.launch {
        val b = repo.observeBundleOnce(projectId) ?: return@launch
        val p = b.product ?: return@launch
        val img = b.assets.firstOrNull { it.kind == AssetKind.ORIGINAL }?.id
        val intel = b.intelligence
        val s = settings.current()
        val card = BlogCard(
            type = type,
            title = when (type) { CardType.HERO -> p.title.take(25); CardType.SPEC -> "제품 정보"; CardType.PRICE -> "작성 시점 가격"; CardType.CTA -> "자세한 정보는 링크에서"; else -> type.label },
            subtitle = if (type == CardType.PRICE) com.shoppingconnect.aistudio.core.common.formatPrice(p.price).takeIf { p.isVerified(com.shoppingconnect.aistudio.domain.model.Product.F_PRICE) } ?: "가격은 판매 페이지에서 확인" else "",
            items = when (type) { CardType.FEATURE -> intel?.keyFeatures; CardType.PROS -> intel?.pros; CardType.CHECK -> intel?.cautions; CardType.TARGET -> intel?.targetAudience; else -> null }.orEmpty().take(4),
            rows = if (type == CardType.SPEC) visuals.specRows(p) else emptyList(),
            cta = if (type == CardType.CTA) "상품 보러가기" else "",
            imageAssetId = if (type in setOf(CardType.HERO, CardType.CTA, CardType.PRICE)) img else null,
            style = CardStyle(preset = b.visualPlan?.stylePreset ?: s.defaultCardStyle, brandText = s.brand.signature.take(24)),
        )
        edit { it + card }
    }

    /** VisualStrategyAgent re-plan of the whole card set. */
    fun replan() = viewModelScope.launch {
        val b = repo.observeBundleOnce(projectId) ?: return@launch
        val p = b.product ?: return@launch
        val a = b.article ?: return@launch
        _busy.value = true
        try {
            val engine = if (b.project.isDemo) DemoAiEngine() else engines.engine()
            val draft = engine.visualPlan(p, a, b.visualPlan?.stylePreset)
            val images = b.assets.filter { it.kind == AssetKind.ORIGINAL && it.mimeType.startsWith("image/") }
            val cards = visuals.buildCards(draft, p, images, a, b.visualPlan?.stylePreset ?: draft.stylePreset, settings.current().brand.signature.take(24))
            store.commit(projectId, b, cards, settings.current().cardExportWidth, cards.map { it.id }.toSet())
        } catch (e: Exception) { _msg.value = e.toAppException().userMessage }
        _busy.value = false
    }

    /** High-res export of every card to the gallery (Pictures/AIStudio). */
    fun exportAll(width: Int) = viewModelScope.launch {
        val b = repo.observeBundleOnce(projectId) ?: return@launch
        _busy.value = true
        var n = 0
        try {
            val pal = visuals.palette(b.visualPlan?.cards?.firstNotNullOfOrNull { it.imageAssetId })
            b.visualPlan?.cards.orEmpty().forEachIndexed { i, c ->
                val a = visuals.renderCard(projectId, c.copy(renderedAssetId = null), width, pal)
                exporter.saveImage(File(a.path), "${b.project.title.take(24)}_card${i + 1}_${width}.jpg"); n++
                media.delete(a)
            }
            _msg.value = "${n}장을 ${width}px로 갤러리에 저장했습니다."
        } catch (e: Exception) { _msg.value = e.toAppException().userMessage }
        _busy.value = false
    }
}
