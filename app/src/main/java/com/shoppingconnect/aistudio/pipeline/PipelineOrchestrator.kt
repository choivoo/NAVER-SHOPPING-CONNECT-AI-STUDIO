package com.shoppingconnect.aistudio.pipeline

import com.shoppingconnect.aistudio.ai.AiEngine
import com.shoppingconnect.aistudio.ai.AiEngineProvider
import com.shoppingconnect.aistudio.ai.claude.AiGateway
import com.shoppingconnect.aistudio.content.ArticleAssembler
import com.shoppingconnect.aistudio.content.ComplianceChecker
import com.shoppingconnect.aistudio.content.ContentMemory
import com.shoppingconnect.aistudio.content.FactGuard
import com.shoppingconnect.aistudio.content.SeoAnalyzer
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.core.common.toAppException
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.data.db.GenerationDao
import com.shoppingconnect.aistudio.data.db.GenerationEntity
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.ContentIssue
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.GenerationState
import com.shoppingconnect.aistudio.domain.model.PipelineMode
import com.shoppingconnect.aistudio.domain.model.PipelineOptions
import com.shoppingconnect.aistudio.domain.model.PipelineStep
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.ProjectStatus
import com.shoppingconnect.aistudio.domain.model.Severity
import com.shoppingconnect.aistudio.domain.model.StepState
import com.shoppingconnect.aistudio.domain.model.StepStatus
import com.shoppingconnect.aistudio.media.shorts.ShortsService
import com.shoppingconnect.aistudio.product.ProductExtractionService
import com.shoppingconnect.aistudio.visual.VisualService
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The multi-agent content pipeline. Each step persists its output and status, so the run can be
 * resumed after process death (WorkManager re-runs the worker and completed steps are skipped).
 *
 *  1 상품 분석(ProductAgent) → 2 데이터 검증(FactAgent) → 3 콘텐츠 전략(StrategyAgent) → 4 글 생성(WriterAgent+SEO)
 *  → 5 이미지 구성(VisualStrategyAgent) → 6 숏폼 제작(ShortformAgent) → 7 Quality Check(Compliance+QC) → 8 완료
 */
@Singleton
class PipelineOrchestrator @Inject constructor(
    private val projects: ProjectRepository,
    private val generations: GenerationDao,
    private val media: MediaRepository,
    private val extractor: ProductExtractionService,
    private val engines: AiEngineProvider,
    private val settingsRepo: SettingsRepository,
    private val visuals: VisualService,
    private val shorts: ShortsService,
    private val gateway: AiGateway,
    private val notifier: Notifier,
) {
    private val stepsSer = ListSerializer(StepStatus.serializer())
    private val warnSer = ListSerializer(String.serializer())

    fun initialSteps() = PipelineStep.entries.map { StepStatus(it) }

    fun decodeSteps(g: GenerationEntity): List<StepStatus> = runCatching { AppJson.decodeFromString(stepsSer, g.stepsJson) }.getOrElse { initialSteps() }
    fun decodeWarnings(g: GenerationEntity): List<String> = runCatching { AppJson.decodeFromString(warnSer, g.warningsJson) }.getOrDefault(emptyList())
    fun decodeOptions(g: GenerationEntity): PipelineOptions = runCatching { AppJson.decodeFromString(PipelineOptions.serializer(), g.optionsJson) }.getOrDefault(PipelineOptions())

    private suspend fun update(genId: String, f: (GenerationEntity) -> GenerationEntity) {
        val g = generations.get(genId) ?: return
        generations.upsert(f(g))
    }

    private suspend fun setStep(genId: String, step: PipelineStep, state: StepState, message: String = "") = update(genId) { g ->
        val steps = decodeSteps(g).map {
            if (it.step != step) it else it.copy(
                state = state, message = message,
                startedAt = if (state == StepState.RUNNING) System.currentTimeMillis() else it.startedAt,
                finishedAt = if (state != StepState.RUNNING) System.currentTimeMillis() else 0,
            )
        }
        g.copy(stepsJson = AppJson.encodeToString(stepsSer, steps))
    }

    private suspend fun warn(genId: String, msg: String) = update(genId) { g ->
        g.copy(warningsJson = AppJson.encodeToString(warnSer, (decodeWarnings(g) + msg).distinct()))
    }

    private suspend fun isDone(genId: String, step: PipelineStep) =
        generations.get(genId)?.let { g -> decodeSteps(g).first { it.step == step }.state in setOf(StepState.COMPLETE, StepState.WARNING, StepState.SKIPPED) } == true

    /** Runs (or resumes) a generation. Never throws for user-facing failures: they are recorded on the step. */
    suspend fun run(genId: String, onProgress: suspend (PipelineStep) -> Unit = {}) {
        val gen = generations.get(genId) ?: return
        val options = decodeOptions(gen)
        val settings = settingsRepo.current()
        update(genId) { it.copy(state = GenerationState.RUNNING, errorKind = null, errorMessage = null) }
        var current = PipelineStep.ANALYZE
        try {
            var product = projects.product(gen.projectId) ?: throw AppException(ErrorKind.ProductExtractionFailed)
            // Demo products always use the clearly-labelled demo engine.
            val engine = if (product.isDemo) com.shoppingconnect.aistudio.ai.DemoAiEngine() else engines.engine()
            update(genId) { it.copy(model = engine.modelLabel, promptVersion = settings.promptVersion) }

            // 1. 상품 분석 -------------------------------------------------------------------
            current = PipelineStep.ANALYZE; onProgress(current)
            if (!isDone(genId, current)) {
                setStep(genId, current, StepState.RUNNING, "상품 정보를 가져오는 중…")
                if (product.title.isBlank() && product.verifiedFields.isEmpty()) {
                    val extracted = extractor.extract(gen.inputUrl)
                    product = extracted.copy(id = product.id)
                    projects.saveProduct(gen.projectId, product, null)
                }
                if (media.list(gen.projectId).none { it.kind == AssetKind.ORIGINAL } && product.images.any { it.startsWith("http") }) {
                    setStep(genId, current, StepState.RUNNING, "상품 이미지를 확보하는 중…")
                    val saved = media.downloadProductImages(gen.projectId, product.images.filter { it.startsWith("http") })
                    if (saved.isEmpty()) warn(genId, "상품 이미지를 내려받지 못했습니다. 이미지를 직접 추가할 수 있습니다.")
                }
                setStep(genId, current, StepState.RUNNING, "AI가 상품 특징을 분석하는 중…")
                val intel = projects.cachedIntelligence(product.sourceUrl).takeUnless { engine.isDemo } ?: engine.analyzeProduct(product)
                projects.saveProduct(gen.projectId, product, intel)
                setStep(genId, current, StepState.COMPLETE, "${product.title.take(30)} 분석 완료")
            }
            val intel = projects.intelligence(gen.projectId) ?: engine.analyzeProduct(product)

            // 2. 데이터 검증 (FactAgent) ----------------------------------------------------
            current = PipelineStep.VERIFY; onProgress(current)
            if (!isDone(genId, current)) {
                setStep(genId, current, StepState.RUNNING)
                val unknown = product.uncertainFields
                val critical = listOf(Product.F_PRICE, Product.F_IMAGES).filter { it in unknown }
                val msg = if (unknown.isEmpty()) "모든 주요 정보 확인됨" else "확인 불가 항목: ${unknown.joinToString()} — 글에서 추측하지 않습니다"
                setStep(genId, current, if (critical.isEmpty()) StepState.COMPLETE else StepState.WARNING, msg)
                if (Product.F_PRICE in unknown) warn(genId, "가격을 확인하지 못해 가격 언급 없이 작성합니다.")
            }

            // 3. 콘텐츠 전략 ----------------------------------------------------------------
            current = PipelineStep.STRATEGY; onProgress(current)
            val hint = options.strategy ?: ContentStrategy(
                preset = settings.defaultPreset, tone = settings.brand.blogTone, length = settings.defaultLength, usage = settings.defaultUsage,
                primaryKeyword = options.keyword.orEmpty(), cta = settings.brand.defaultCta,
            )
            var strategy = hint
            val existing = projects.observeBundleOnce(gen.projectId)
            if (!isDone(genId, current) || existing?.strategy == null) {
                setStep(genId, current, StepState.RUNNING, "검색 의도와 키워드를 분석하는 중…")
                strategy = engine.strategy(product, intel, hint)
                setStep(genId, current, StepState.COMPLETE, "${strategy.preset.label} · 키워드 \"${strategy.primaryKeyword}\"")
            } else strategy = existing.strategy

            // 4. 글 생성 --------------------------------------------------------------------
            current = PipelineStep.WRITE; onProgress(current)
            var article: Article = existing?.article ?: Article()
            if (!isDone(genId, current) || article.blocks.isEmpty()) {
                setStep(genId, current, StepState.RUNNING, "블로그 본문을 작성하는 중…")
                val others = projects.otherArticles(gen.projectId)
                val avoid = ContentMemory.frequentPhrases(others.map { it.second })
                val draft = engine.write(product, intel, strategy, avoid)
                article = ArticleAssembler.assemble(draft, product, strategy, settings)
                setStep(genId, current, StepState.RUNNING, "제목 후보를 만드는 중…")
                val titles = runCatching { engine.titles(product, strategy, article) }.getOrElse {
                    warn(genId, "제목 후보 생성 실패: ${it.toAppException().kind.userMessage}"); emptyList()
                }
                article = article.copy(title = titles.firstOrNull()?.text ?: product.title.take(40), titleCandidates = titles)
                val score = SeoAnalyzer.analyze(article, strategy.primaryKeyword, 0).score
                projects.saveArticle(gen.projectId, article, strategy, "Draft 1 (AI)", engine.modelLabel, settings.promptVersion, score)
                setStep(genId, current, StepState.COMPLETE, "${article.charCount}자 · 제목 후보 ${titles.size}개")
            }

            // 5. 이미지 구성 ----------------------------------------------------------------
            current = PipelineStep.VISUALS; onProgress(current)
            var cards = existing?.visualPlan?.cards.orEmpty()
            if (!options.includeVisuals) {
                setStep(genId, current, StepState.SKIPPED, "사용자 설정으로 건너뜀")
            } else if (!isDone(genId, current) || cards.isEmpty()) {
                setStep(genId, current, StepState.RUNNING, "블로그 이미지 카드를 디자인하는 중…")
                val draft = engine.visualPlan(product, article, options.imageStyle)
                val images = media.list(gen.projectId).filter { it.kind == AssetKind.ORIGINAL && it.mimeType.startsWith("image/") }
                cards = visuals.buildCards(draft, product, images, article, options.imageStyle ?: draft.stylePreset, settings.brand.signature.take(24))
                cards = visuals.renderAll(gen.projectId, cards, settings.cardExportWidth)
                article = visuals.placeInArticle(article, cards)
                val score = SeoAnalyzer.analyze(article, strategy.primaryKeyword, cards.size + images.size.coerceAtMost(3)).score
                projects.saveArticle(gen.projectId, article, strategy, null, engine.modelLabel, settings.promptVersion, score, visuals.plan(draft, cards))
                setStep(genId, current, StepState.COMPLETE, "카드 ${cards.size}장 생성 · 본문 배치 완료")
            }

            // 6. 숏폼 제작 ------------------------------------------------------------------
            current = PipelineStep.SHORTS; onProgress(current)
            if (!options.includeShorts) {
                setStep(genId, current, StepState.SKIPPED, "사용자 설정으로 건너뜀")
            } else if (!isDone(genId, current) || existing?.timeline == null) {
                setStep(genId, current, StepState.RUNNING, "스토리보드와 내레이션을 만드는 중…")
                val r = shorts.createDraft(
                    engine, gen.projectId, product, article, cards, settings,
                    options.template ?: settings.shortsTemplate, options.shortsDurationSec ?: settings.shortsDurationSec, strategy.usage,
                )
                val tl = r.timeline.copy(voice = options.voicePreset?.let { r.timeline.voice.copy(preset = it) } ?: r.timeline.voice)
                projects.saveShortform(gen.projectId, tl, r.thumbnail, thumbnailPath = r.thumbnailAsset?.path)
                r.warnings.forEach { warn(genId, it) }
                setStep(genId, current, if (r.warnings.isEmpty()) StepState.COMPLETE else StepState.WARNING, "${tl.scenes.size}개 장면 · ${tl.durationMs / 1000}초 드래프트")
            }

            // 7. Quality Check --------------------------------------------------------------
            current = PipelineStep.QC; onProgress(current)
            setStep(genId, current, StepState.RUNNING, "사실 검증 및 광고 표시 검사 중…")
            val issues = qualityCheck(engine, gen.projectId, product, article, strategy, settings)
            val errors = issues.count { it.severity == Severity.ERROR }
            issues.filter { it.severity != Severity.INFO }.take(8).forEach { warn(genId, it.message) }
            setStep(genId, current, if (errors == 0) StepState.COMPLETE else StepState.WARNING, if (issues.isEmpty()) "문제 없음" else "확인 필요 ${issues.size}건 (오류 $errors)")

            // 8. 완료 ------------------------------------------------------------------------
            current = PipelineStep.DONE; onProgress(current)
            projects.setStatus(gen.projectId, ProjectStatus.GENERATED)
            setStep(genId, current, StepState.COMPLETE, if (engine.isDemo) "데모 모드 결과입니다" else "검수 후 게시하세요")
            val meta = gateway.lastMeta
            update(genId) { it.copy(state = if (decodeWarnings(it).isEmpty()) GenerationState.SUCCEEDED else GenerationState.PARTIAL, finishedAt = System.currentTimeMillis(), inputTokens = it.inputTokens + (meta?.inputTokens ?: 0), outputTokens = it.outputTokens + (meta?.outputTokens ?: 0)) }
            notifier.done(gen.projectId, "AI 콘텐츠 생성이 완료되었습니다.", product.title.take(40))
        } catch (e: Throwable) {
            val ex = e.toAppException()
            if (ex.kind == ErrorKind.Cancelled) {
                update(genId) { it.copy(state = GenerationState.CANCELLED, finishedAt = System.currentTimeMillis()) }
                setStep(genId, current, StepState.ERROR, "취소됨")
                throw e
            }
            AppLog.e("Pipeline", "step $current failed: ${ex.kind}", e)
            setStep(genId, current, StepState.ERROR, ex.userMessage)
            update(genId) { it.copy(state = GenerationState.FAILED, errorKind = ex.kind.name, errorMessage = ex.userMessage, finishedAt = System.currentTimeMillis()) }
        }
    }

    /** ComplianceAgent + QC Agent + Duplicate Detector. */
    suspend fun qualityCheck(engine: AiEngine, projectId: String, product: Product, article: Article, strategy: ContentStrategy, settings: AppSettings): List<ContentIssue> {
        val issues = mutableListOf<ContentIssue>()
        issues += ComplianceChecker.check(article, strategy.usage, disclosureRequired = settings.disclosureText.isNotBlank())
        issues += FactGuard.check(article, product)
        ContentMemory.duplicateIssue(ContentMemory.mostSimilar(article, projects.otherArticles(projectId)))?.let { issues += it }
        if (!engine.isDemo) runCatching { engine.compliance(product, com.shoppingconnect.aistudio.content.ArticleFormatter.toPlainText(article), strategy.usage) }
            .onSuccess { ai -> issues += ai.filter { a -> issues.none { it.excerpt != null && it.excerpt == a.excerpt } } }
            .onFailure { AppLog.w("Pipeline", "AI compliance review skipped", it) }
        return issues
    }

    fun modeLabel(mode: PipelineMode) = when (mode) { PipelineMode.QUICK -> "Quick"; PipelineMode.PRO -> "Pro"; PipelineMode.MAGIC -> "⚡ 전체 자동" }
}
