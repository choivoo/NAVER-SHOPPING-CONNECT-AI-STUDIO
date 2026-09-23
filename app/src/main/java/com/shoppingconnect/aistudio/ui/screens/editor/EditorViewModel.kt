package com.shoppingconnect.aistudio.ui.screens.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.ai.AiEngine
import com.shoppingconnect.aistudio.ai.AiEngineProvider
import com.shoppingconnect.aistudio.ai.DemoAiEngine
import com.shoppingconnect.aistudio.ai.RewriteAction
import com.shoppingconnect.aistudio.content.ArticleAssembler
import com.shoppingconnect.aistudio.content.ComplianceChecker
import com.shoppingconnect.aistudio.content.ContentMemory
import com.shoppingconnect.aistudio.content.FactGuard
import com.shoppingconnect.aistudio.content.KoreanText
import com.shoppingconnect.aistudio.content.SeoAnalyzer
import com.shoppingconnect.aistudio.core.common.toAppException
import com.shoppingconnect.aistudio.data.db.ArticleVersionEntity
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.Block
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.ContentIssue
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.CopyrightType
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.QualityReport
import com.shoppingconnect.aistudio.domain.model.TitleCandidate
import com.shoppingconnect.aistudio.media.shorts.History
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorState(
    val loaded: Boolean = false,
    val article: Article = Article(),
    val strategy: ContentStrategy = ContentStrategy(),
    val product: Product? = null,
    val isDemo: Boolean = false,
    val selectedId: String? = null,
    val selection: IntRange? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val aiBusy: Boolean = false,
    val titleBusy: Boolean = false,
    val issues: List<ContentIssue> = emptyList(),
    val quality: QualityReport = QualityReport(0, emptyList()),
    val message: String? = null,
    val saving: Boolean = false,
    val savedAt: Long = 0,
    val repeated: List<String> = emptyList(),
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ProjectRepository,
    private val media: MediaRepository,
    private val engines: AiEngineProvider,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    val projectId: String = checkNotNull(handle["projectId"])
    private val _s = MutableStateFlow(EditorState())
    val s: StateFlow<EditorState> = _s.asStateFlow()
    val assets: StateFlow<List<MediaAssetEntity>> = media.observe(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val articleId = MutableStateFlow<String?>(null)
    val versions: StateFlow<List<ArticleVersionEntity>> = articleId.flatMapLatest { id -> if (id == null) emptyFlow() else repo.observeVersions(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private lateinit var history: History<Article>
    private var settings = AppSettings()
    private var saveJob: Job? = null
    private var checkJob: Job? = null
    private var others: List<Pair<String, Article>> = emptyList()

    init {
        viewModelScope.launch {
            settings = settingsRepo.current()
            val b = repo.observeBundleOnce(projectId)
            val article = b?.article ?: Article()
            history = History(article)
            articleId.value = b?.articleId
            others = repo.otherArticles(projectId)
            _s.update { it.copy(loaded = true, article = article, strategy = b?.strategy ?: ContentStrategy(), product = b?.product, isDemo = b?.project?.isDemo == true) }
            recheck()
        }
    }

    private suspend fun engine(): AiEngine = if (_s.value.isDemo) DemoAiEngine() else engines.engine()

    // ---- editing -----------------------------------------------------------------------------

    private fun commit(next: Article, snapshot: Boolean = true) {
        if (snapshot) history.push(next) else history.replace(next)
        _s.update { it.copy(article = next, canUndo = history.canUndo, canRedo = history.canRedo) }
        scheduleSave(); scheduleCheck()
    }

    /** Typing replaces the current snapshot; structural changes push a new undo step. */
    private var lastTypingBlock: String? = null
    fun updateBlock(id: String, f: (Block) -> Block) {
        val a = _s.value.article
        val next = a.copy(blocks = a.blocks.map { if (it.id == id) f(it).copy(aiGenerated = false) else it })
        commit(next, snapshot = lastTypingBlock != id)
        lastTypingBlock = id
    }

    fun updateTitle(t: String) { commit(_s.value.article.copy(title = t), snapshot = lastTypingBlock != "title"); lastTypingBlock = "title" }
    fun updateHashtags(raw: String) = commit(_s.value.article.copy(hashtags = raw.split(' ', ',', '\n').map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct()))

    fun select(id: String?, selection: IntRange? = null) { _s.update { it.copy(selectedId = id, selection = selection) }; if (id != lastTypingBlock) lastTypingBlock = null }

    fun insert(type: BlockType, assetId: String? = null) {
        lastTypingBlock = null
        val a = _s.value.article
        val block = when (type) {
            BlockType.DISCLOSURE -> Block(type = type, text = settings.disclosureText, aiGenerated = false)
            BlockType.PRODUCT_CARD -> _s.value.product?.let { ArticleAssembler.productCard(it, settings) } ?: Block(type = type, text = "제품 정보 요약", aiGenerated = false)
            BlockType.CTA -> Block(type = type, text = settings.brand.defaultCta, url = _s.value.product?.affiliateUrl, aiGenerated = false)
            BlockType.LINK -> Block(type = type, text = "상품 자세히 보기", url = _s.value.product?.affiliateUrl, aiGenerated = false)
            BlockType.IMAGE -> Block(type = type, assetId = assetId, text = "", aiGenerated = false)
            BlockType.LIST -> Block(type = type, items = listOf(""), aiGenerated = false)
            else -> Block(type = type, aiGenerated = false)
        }
        val idx = a.blocks.indexOfFirst { it.id == _s.value.selectedId }.let { if (it < 0) a.blocks.size else it + 1 }
        commit(a.copy(blocks = a.blocks.toMutableList().apply { add(idx, block) }))
        _s.update { it.copy(selectedId = block.id) }
    }

    fun insertImageAt(assetId: String, beforeBlockId: String?) {
        val a = _s.value.article
        val b = Block(type = BlockType.IMAGE, assetId = assetId, aiGenerated = false)
        val idx = beforeBlockId?.let { id -> a.blocks.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: a.blocks.size
        commit(a.copy(blocks = a.blocks.toMutableList().apply { add(idx, b) }))
    }

    fun delete(id: String) { lastTypingBlock = null; val a = _s.value.article; commit(a.copy(blocks = a.blocks.filterNot { it.id == id })) }

    fun move(id: String, delta: Int) {
        lastTypingBlock = null
        val list = _s.value.article.blocks.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        val b = list.removeAt(i); list.add(j, b)
        commit(_s.value.article.copy(blocks = list))
    }

    fun changeType(id: String, type: BlockType) = updateBlock(id) { b ->
        if (type == BlockType.LIST && b.type != BlockType.LIST) b.copy(type = type, items = b.text.lines().filter { it.isNotBlank() }, text = "")
        else if (b.type == BlockType.LIST && type != BlockType.LIST) b.copy(type = type, text = b.items.joinToString(" "), items = emptyList())
        else b.copy(type = type)
    }

    /** Wraps the current text selection (or whole block) in **bold**. */
    fun bold() {
        val st = _s.value
        val id = st.selectedId ?: return
        lastTypingBlock = null
        updateBlock(id) { b ->
            val r = st.selection?.takeIf { it.first < it.last + 1 && it.last < b.text.length }
            if (r == null || r.isEmpty()) b.copy(text = if (b.text.startsWith("**") && b.text.endsWith("**")) b.text.removeSurrounding("**") else "**${b.text}**")
            else b.copy(text = b.text.substring(0, r.first) + "**" + b.text.substring(r.first, r.last + 1) + "**" + b.text.substring(r.last + 1))
        }
    }

    fun undo() { history.undo()?.let { a -> _s.update { it.copy(article = a, canUndo = history.canUndo, canRedo = history.canRedo) }; scheduleSave(); scheduleCheck() } }
    fun redo() { history.redo()?.let { a -> _s.update { it.copy(article = a, canUndo = history.canUndo, canRedo = history.canRedo) }; scheduleSave(); scheduleCheck() } }

    fun splitLongParagraphs() {
        val a = _s.value.article
        commit(a.copy(blocks = a.blocks.flatMap { b -> if (b.type == BlockType.PARAGRAPH && b.text.length > 260) KoreanText.splitParagraph(b.text).mapIndexed { i, t -> if (i == 0) b.copy(text = t) else Block(type = BlockType.PARAGRAPH, text = t, aiGenerated = b.aiGenerated) } else listOf(b) }))
    }

    /** Quick fix for fake-experience phrasing and missing disclosure. */
    fun fix(issue: ContentIssue) {
        when (issue.code) {
            "FAKE_EXPERIENCE" -> issue.blockId?.let { id -> updateBlock(id) { b -> b.copy(text = ComplianceChecker.neutralize(b.text), items = b.items.map { ComplianceChecker.neutralize(it) }) } }
                ?: updateTitle(ComplianceChecker.neutralize(_s.value.article.title).replace("후기", "정리"))
            "MISSING_DISCLOSURE" -> { val a = _s.value.article; commit(a.copy(blocks = listOf(Block(type = BlockType.DISCLOSURE, text = settings.disclosureText, aiGenerated = false)) + a.blocks)) }
            else -> issue.blockId?.let { select(it) }
        }
    }

    fun refreshProductCard() {
        val p = _s.value.product ?: return
        val a = _s.value.article
        val card = ArticleAssembler.productCard(p, settings)
        commit(a.copy(blocks = a.blocks.map { if (it.type == BlockType.PRODUCT_CARD) card.copy(id = it.id) else it }))
    }

    // ---- AI ----------------------------------------------------------------------------------

    fun rewrite(action: RewriteAction) {
        val st = _s.value
        val block = st.article.blocks.firstOrNull { it.id == st.selectedId } ?: run { _s.update { it.copy(message = "먼저 다듬을 문단을 선택하세요.") }; return }
        val product = st.product ?: return
        val source = if (block.type == BlockType.LIST) block.items.joinToString("\n") else block.text
        val sel = st.selection?.takeIf { !it.isEmpty() && block.type != BlockType.LIST && it.last < source.length }
        val target = sel?.let { source.substring(it.first, it.last + 1) } ?: source
        if (target.isBlank()) return
        _s.update { it.copy(aiBusy = true, message = null) }
        viewModelScope.launch {
            try {
                val out = engine().rewrite(product, target, action, st.strategy.usage).let { if (st.strategy.usage == com.shoppingconnect.aistudio.domain.model.UsageStatus.USED) it else ComplianceChecker.neutralize(it) }
                val a = _s.value.article
                val updated = a.blocks.map { b ->
                    if (b.id != block.id) b else when {
                        action == RewriteAction.HEADING -> b.copy(type = BlockType.H2, text = out.lines().first(), items = emptyList(), aiGenerated = true)
                        b.type == BlockType.LIST -> b.copy(items = out.lines().map { it.trim().removePrefix("•").removePrefix("-").trim() }.filter { it.isNotBlank() }, aiGenerated = true)
                        sel != null -> b.copy(text = source.substring(0, sel.first) + out + source.substring(sel.last + 1), aiGenerated = true)
                        else -> b.copy(text = out, aiGenerated = true)
                    }
                }
                lastTypingBlock = null
                commit(a.copy(blocks = updated))
                saveVersion("AI Rewrite · ${action.label}")
                _s.update { it.copy(aiBusy = false) }
            } catch (e: Exception) {
                _s.update { it.copy(aiBusy = false, message = e.toAppException().userMessage) }
            }
        }
    }

    fun regenerateTitles() {
        val st = _s.value
        val product = st.product ?: return
        _s.update { it.copy(titleBusy = true) }
        viewModelScope.launch {
            try {
                val t = engine().titles(product, st.strategy, st.article)
                // A/B: keep previous candidates too.
                commit(_s.value.article.copy(titleCandidates = (t + _s.value.article.titleCandidates).distinctBy { it.text }.take(30)))
                _s.update { it.copy(titleBusy = false) }
            } catch (e: Exception) { _s.update { it.copy(titleBusy = false, message = e.toAppException().userMessage) } }
        }
    }

    fun chooseTitle(t: TitleCandidate) { lastTypingBlock = null; commit(_s.value.article.copy(title = t.text)) }

    fun importImage(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { media.importUri(projectId, uri, CopyrightType.USER_UPLOAD, AssetKind.ORIGINAL) }
            .onSuccess { insert(BlockType.IMAGE, it.id) }
            .onFailure { e -> _s.update { it.copy(message = e.toAppException().userMessage) } }
    }

    fun dismissMessage() = _s.update { it.copy(message = null) }

    // ---- persistence & checks -----------------------------------------------------------------

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1200); save(null) }
    }

    private suspend fun save(label: String?) {
        val st = _s.value
        _s.update { it.copy(saving = true) }
        val images = st.article.blocks.count { it.type == BlockType.IMAGE }
        val q = SeoAnalyzer.analyze(st.article, st.strategy.primaryKeyword, images)
        val id = repo.saveArticle(projectId, st.article, st.strategy, label, "", settings.promptVersion, q.score, markEdited = true)
        articleId.value = id
        _s.update { it.copy(saving = false, savedAt = System.currentTimeMillis()) }
    }

    fun saveVersion(label: String) = viewModelScope.launch { saveJob?.cancel(); save(label) }

    fun restore(v: ArticleVersionEntity) = viewModelScope.launch {
        repo.version(v.id)?.let { a -> lastTypingBlock = null; commit(a); save("Restore · ${v.label}") }
    }

    private fun scheduleCheck() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch { delay(700); recheck() }
    }

    private fun recheck() {
        val st = _s.value
        val a = st.article
        val issues = buildList {
            addAll(ComplianceChecker.check(a, st.strategy.usage, settings.disclosureText.isNotBlank()))
            st.product?.let { addAll(FactGuard.check(a, it)) }
            ContentMemory.duplicateIssue(ContentMemory.mostSimilar(a, others))?.let { add(it) }
        }
        val images = a.blocks.count { it.type == BlockType.IMAGE }
        _s.update { it.copy(issues = issues, quality = SeoAnalyzer.analyze(a, st.strategy.primaryKeyword, images), repeated = ContentMemory.repeatedSentences(a)) }
    }

    fun setKeyword(k: String) { _s.update { it.copy(strategy = it.strategy.copy(primaryKeyword = k)) }; scheduleSave(); scheduleCheck() }

    override fun onCleared() {
        // flush pending autosave
        if (saveJob?.isActive == true) kotlinx.coroutines.runBlocking { save(null) }
        super.onCleared()
    }
}
