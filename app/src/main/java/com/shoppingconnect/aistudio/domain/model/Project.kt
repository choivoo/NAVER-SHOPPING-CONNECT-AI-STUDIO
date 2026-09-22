package com.shoppingconnect.aistudio.domain.model

import kotlinx.serialization.Serializable

enum class ProjectStatus(val label: String) { DRAFT("Draft"), GENERATED("Generated"), EDITED("Edited"), READY("Ready"), PUBLISHED("Published"), ARCHIVED("Archived") }

enum class AssetKind(val label: String) { ORIGINAL("Original"), GENERATED("Generated"), EDITED("Edited"), BLOG("Blog"), SHORTS("Shorts"), THUMBNAIL("Thumbnail"), VIDEO("Video"), AUDIO("Audio") }

enum class CopyrightType(val label: String) {
    PRODUCT_SOURCE("상품 출처 제공 이미지"), USER_UPLOAD("사용자 업로드"), USER_LICENSED("사용 권한 보유"),
    AI_GENERATED("AI 생성 그래픽"), APP_GENERATED("앱 생성 그래픽"), APP_SYNTHESIZED_AUDIO("앱 합성 음원"), TTS("TTS 음성"),
}

enum class PipelineStep(val index: Int, val label: String) {
    ANALYZE(1, "상품 분석"), VERIFY(2, "데이터 검증"), STRATEGY(3, "콘텐츠 전략"), WRITE(4, "글 생성"),
    VISUALS(5, "이미지 구성"), SHORTS(6, "숏폼 제작"), QC(7, "Quality Check"), DONE(8, "완료");

    companion object { const val TOTAL = 8 }
}

enum class StepState { WAITING, RUNNING, COMPLETE, WARNING, ERROR, SKIPPED }

@Serializable
data class StepStatus(val step: PipelineStep, val state: StepState = StepState.WAITING, val message: String = "", val startedAt: Long = 0, val finishedAt: Long = 0)

enum class PipelineMode { QUICK, PRO, MAGIC }

@Serializable
data class PipelineOptions(
    val mode: PipelineMode = PipelineMode.QUICK,
    val includeShorts: Boolean = true,
    val includeVisuals: Boolean = true,
    val strategy: ContentStrategy? = null,
    val shortsDurationSec: Int? = null,
    val template: ShortsTemplateId? = null,
    val voicePreset: VoicePreset? = null,
    val imageStyle: CardStylePreset? = null,
    val keyword: String? = null,
)

enum class GenerationState { QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED, CANCELLED }
