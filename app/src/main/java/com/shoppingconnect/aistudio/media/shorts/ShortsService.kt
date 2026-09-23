package com.shoppingconnect.aistudio.media.shorts

import com.shoppingconnect.aistudio.ai.AiEngine
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.data.files.ProjectFiles
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.BlogCard
import com.shoppingconnect.aistudio.domain.model.CopyrightType
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.RenderSettings
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.ThumbnailSpec
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import com.shoppingconnect.aistudio.domain.model.VoiceSettings
import com.shoppingconnect.aistudio.media.subtitle.KeywordHighlighter
import com.shoppingconnect.aistudio.media.tts.AndroidTtsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ShortsDraftResult(val timeline: ShortTimeline, val thumbnail: ThumbnailSpec, val thumbnailAsset: MediaAssetEntity?, val warnings: List<String>)

@Singleton
class ShortsService @Inject constructor(
    private val media: MediaRepository,
    private val tts: AndroidTtsProvider,
    private val files: ProjectFiles,
) {
    fun renderSettings(s: AppSettings) = RenderSettings(s.shortsResolution, s.shortsFps, s.shortsQuality, s.shortsHevc, s.exportProfile)

    fun voiceSettings(s: AppSettings) = VoiceSettings(enabled = s.voiceEnabled, preset = s.voicePreset, voiceName = s.ttsVoiceName.ifBlank { null })

    /** One-tap AI auto edit → draft timeline with synced voice and a thumbnail. */
    suspend fun createDraft(
        engine: AiEngine, projectId: String, product: Product, article: Article, cards: List<BlogCard>,
        settings: AppSettings, templateId: ShortsTemplateId, durationSec: Int, usage: UsageStatus,
    ): ShortsDraftResult {
        val warnings = mutableListOf<String>()
        val draft = engine.shortform(product, article, durationSec, templateId, usage)
        val assets = media.list(projectId)
        val images = assets.filter { it.kind == AssetKind.ORIGINAL && it.mimeType.startsWith("image/") }.map { AssetRefForShorts(it.id, it.path) }
        val cardImages = cards.mapNotNull { c -> c.renderedAssetId?.let { id -> assets.firstOrNull { it.id == id } }?.let { c.type.name to AssetRefForShorts(it.id, it.path) } }.toMap()
        if (images.isEmpty()) warnings += "상품 이미지가 없어 카드 그래픽으로 장면을 구성했습니다."
        val template = ShortsTemplates.of(templateId)
        var timeline = AutoEditor.build(
            draft, product, images, cardImages, template, durationSec, voiceSettings(settings), renderSettings(settings),
            settings.subtitlesEnabled, if (settings.watermarkEnabled) settings.watermarkText.ifBlank { null } else null,
        )
        if (settings.voiceEnabled) {
            val result = runCatching { synthesize(projectId, timeline, product) }
            result.onSuccess { timeline = it }.onFailure {
                AppLog.w("Shorts", "TTS unavailable", it)
                warnings += "TTS를 사용할 수 없어 음성 없이 자막만 구성했습니다: ${it.message ?: ""}".trim()
            }
            if (VoiceSync.overTarget(timeline)) warnings += "내레이션이 길어 영상이 목표 길이보다 깁니다. Shorts 편집기에서 'AI 축약'을 사용해 보세요."
        }
        val thumbTexts = runCatching { engine.thumbnailTexts(product) }.getOrNull()
        val spec = ThumbnailSpec(
            title = thumbTexts?.titles?.firstOrNull() ?: draft.thumbnailTexts.firstOrNull() ?: product.title.take(12),
            hook = thumbTexts?.hooks?.firstOrNull() ?: draft.hooks.firstOrNull()?.text.orEmpty(),
            imageAssetId = images.firstOrNull()?.id ?: cardImages["HERO"]?.id,
            candidates = (thumbTexts?.titles.orEmpty() + draft.thumbnailTexts).distinct().take(8),
        )
        val thumbAsset = runCatching { renderThumbnail(projectId, spec) }.getOrNull()
        return ShortsDraftResult(timeline, spec, thumbAsset, warnings)
    }

    /** Synthesises narration per scene and re-times the whole timeline to the real durations. */
    suspend fun synthesize(projectId: String, timeline: ShortTimeline, product: Product): ShortTimeline {
        val dir = files.dir(projectId, AssetKind.AUDIO)
        timeline.voiceClips.mapNotNull { it.path }.forEach { p -> if (files.isInside(p)) File(p).delete() }
        val results = LinkedHashMap<String, Pair<String, Long>>()
        for (s in timeline.scenes) {
            if (s.narration.isBlank()) continue
            val r = tts.synthesize(s.narration, timeline.voice, dir)
            results[s.id] = r.file.absolutePath to r.durationMs
        }
        return VoiceSync.apply(timeline, ShortsTemplates.of(timeline.template), results, KeywordHighlighter.factTokens(product))
    }

    suspend fun renderThumbnail(projectId: String, spec: ThumbnailSpec, replaceId: String? = null): MediaAssetEntity = withContext(Dispatchers.Default) {
        val img = media.loadBitmap(spec.imageAssetId, 1440)
        val bmp = ThumbnailRenderer.render(spec, img)
        img?.recycle()
        media.saveBitmap(projectId, bmp, AssetKind.THUMBNAIL, "thumbnail", null, CopyrightType.APP_GENERATED, replaceId = replaceId).also { bmp.recycle() }
    }

    fun outputFile(projectId: String): File = files.newFile(projectId, AssetKind.VIDEO, "shorts_${newId().take(8)}.mp4")

}
