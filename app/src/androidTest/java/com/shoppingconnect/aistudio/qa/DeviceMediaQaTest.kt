package com.shoppingconnect.aistudio.qa

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.shoppingconnect.aistudio.ai.AiEngineProvider
import com.shoppingconnect.aistudio.ai.claude.AiGateway
import com.shoppingconnect.aistudio.ai.claude.ClaudeClient
import com.shoppingconnect.aistudio.ai.claude.ModelResolver
import com.shoppingconnect.aistudio.ai.prompts.PromptRepository
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.core.network.SafeFetcher
import com.shoppingconnect.aistudio.core.security.InMemorySecretStore
import com.shoppingconnect.aistudio.data.db.AppDatabase
import com.shoppingconnect.aistudio.data.db.GenerationEntity
import com.shoppingconnect.aistudio.data.files.ProjectFiles
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.data.repository.ProjectBundle
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.InMemorySettingsRepository
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.BlogCard
import com.shoppingconnect.aistudio.domain.model.CardType
import com.shoppingconnect.aistudio.domain.model.CopyrightType
import com.shoppingconnect.aistudio.domain.model.GenerationState
import com.shoppingconnect.aistudio.domain.model.PipelineOptions
import com.shoppingconnect.aistudio.domain.model.Spec
import com.shoppingconnect.aistudio.domain.model.StepStatus
import com.shoppingconnect.aistudio.domain.model.VoiceSettings
import com.shoppingconnect.aistudio.media.export.MediaExporter
import com.shoppingconnect.aistudio.media.shorts.ShortsService
import com.shoppingconnect.aistudio.media.tts.AndroidTtsProvider
import com.shoppingconnect.aistudio.media.video.CodecSupport
import com.shoppingconnect.aistudio.media.video.ShortsRenderer
import com.shoppingconnect.aistudio.pipeline.DemoData
import com.shoppingconnect.aistudio.pipeline.Notifier
import com.shoppingconnect.aistudio.pipeline.PipelineOrchestrator
import com.shoppingconnect.aistudio.product.ProductExtractionService
import com.shoppingconnect.aistudio.visual.CardRenderer
import com.shoppingconnect.aistudio.visual.VisualService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * Real-runtime media QA (emulator or phone): Android TTS synthesis, the demo pipeline with voice,
 * the MediaCodec/MediaMuxer MP4 render verified with MediaExtractor/MediaMetadataRetriever,
 * MediaStore export, FileProvider share URIs and Korean card rendering at 1080/2160 px.
 * Uses an in-memory database so the app's own projects are untouched.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DeviceMediaQaTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun a_encoders() {
        val all = CodecSupport.candidates(preferHevc = false, width = 1080, height = 1920, fps = 30)
        QaRecorder.record("MediaCodec encoders", "MEASURED", "candidates" to all.joinToString { "${it.codecName}(hw=${it.hardware}) ${it.width}x${it.height}" })
        assertWithMessage("no H.264 surface encoder for 1080x1920@30").that(all).isNotEmpty()
    }

    @Test fun b_koreanTts() = runBlocking {
        val tts = AndroidTtsProvider(ctx)
        val available = tts.isAvailable()
        val voices = runCatching { tts.voices() }.getOrDefault(emptyList())
        if (!available) {
            // Guidance path: synthesis must fail with a user-facing error, never crash.
            val err = runCatching { tts.synthesize(SENTENCE, VoiceSettings(), File(ctx.cacheDir, "qa_tts")) }.exceptionOrNull()
            QaRecorder.record("TTS Korean", "NOT AVAILABLE", "voices" to voices.size, "error" to err?.message)
            tts.shutdown()
            return@runBlocking
        }
        val started = System.currentTimeMillis()
        val r = tts.synthesize(SENTENCE, VoiceSettings(), File(ctx.cacheDir, "qa_tts"))
        val ms = System.currentTimeMillis() - started
        tts.shutdown()
        QaRecorder.record("TTS Korean", "MEASURED", "voices" to voices.map { it.name }.toString(), "audioMs" to r.durationMs, "synthMs" to ms, "bytes" to r.file.length())
        assertThat(r.durationMs).isGreaterThan(1500L) // ~30 syllables of speech
        assertThat(r.durationMs).isLessThan(15_000L)
    }

    @Test fun c_cards1080and2160() {
        val img = DemoData.illustration()
        val cards = listOf(
            BlogCard(type = CardType.HERO, title = "노이즈 캔슬링 무선 이어폰", subtitle = "하루 종일 조용하게, 최대 30시간 재생"),
            BlogCard(type = CardType.FEATURE, title = "핵심 특징 3가지", items = listOf("하이브리드 ANC -35dB", "블루투스 5.3 멀티포인트", "IPX4 생활 방수 😀")),
            BlogCard(type = CardType.SPEC, title = "제품 정보", rows = listOf(Spec("무게", "5.3g"), Spec("배터리", "1,200mAh 케이스"), Spec("코덱", "AAC / SBC"))),
            BlogCard(type = CardType.CTA, title = "지금 확인해 보세요", cta = "상품 보러 가기"),
        )
        for (w in listOf(1080, 2160)) for (card in cards) {
            val bmp = CardRenderer.render(card, w, img, null)
            val v = QaRecorder.variance(bmp)
            QaRecorder.saveBitmap("card_${card.type.name.lowercase()}_$w", bmp)
            QaRecorder.record("Card ${card.type.name} ${w}px", "MEASURED", "size" to "${bmp.width}x${bmp.height}", "variance" to v)
            assertThat(bmp.width).isEqualTo(w)
            assertWithMessage("${card.type} at $w px looks blank").that(v).isGreaterThan(20.0)
            bmp.recycle()
        }
    }

    @Test fun d_pipelineRenderExportShare() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        try {
            val bundle = runDemoPipeline(db)
            val timeline = bundle.timeline!!
            QaRecorder.record("Demo pipeline (voice on)", "MEASURED", "scenes" to timeline.scenes.size, "voiceClips" to timeline.voiceClips.size, "timelineMs" to timeline.durationMs)

            val out = File(ctx.cacheDir, "qa_short.mp4").apply { delete() }
            val t0 = System.currentTimeMillis()
            val stats = ShortsRenderer(timeline) { s -> s.mediaPath }.render(out, onProgress = {}, isCancelled = { false })
            val wallMs = System.currentTimeMillis() - t0
            val probe = probe(out)
            QaRecorder.record(
                "Shorts render 1080x1920 H.264", "MEASURED", "codecName" to stats.codecName, "hardware" to stats.hardware, "renderMs" to wallMs,
                "frames" to stats.frames, "bytes" to out.length(), "videoMime" to probe.videoMime, "resolution" to "${probe.width}x${probe.height}",
                "fps" to probe.fps, "videoMs" to probe.videoMs, "audioMime" to probe.audioMime, "audioMs" to probe.audioMs, "rotation" to probe.rotation,
                "timelineMs" to timeline.durationMs, "minFrameVariance" to probe.minFrameVariance,
            )
            assertThat(probe.videoMime).isEqualTo(MediaFormat.MIMETYPE_VIDEO_AVC)
            assertThat(probe.width to probe.height).isEqualTo(1080 to 1920)
            assertThat(probe.fps).isWithin(1.0).of(30.0)
            assertThat(probe.videoMs).isWithin(400L).of(timeline.durationMs)
            assertThat(probe.audioMime).isEqualTo(MediaFormat.MIMETYPE_AUDIO_AAC)
            assertThat(probe.audioMs).isWithin(400L).of(timeline.durationMs)
            assertThat(probe.rotation).isEqualTo(0)
            assertWithMessage("black or frozen frames in the render").that(probe.minFrameVariance).isGreaterThan(20.0)

            val exporter = MediaExporter(ctx)
            val uri = exporter.saveVideo(out, "qa_short_${System.currentTimeMillis()}.mp4")
            val visible = ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)!!.use { c ->
                c.moveToFirst(); Triple(c.getLong(0), c.getInt(1), c.getString(2))
            }
            QaRecorder.record("Gallery save (MediaStore)", "MEASURED", "uri" to uri.toString(), "size" to visible.first, "pending" to visible.second, "path" to visible.third)
            assertThat(visible.first).isEqualTo(out.length())
            assertThat(visible.second).isEqualTo(0)
            ctx.contentResolver.delete(uri, null, null)

            val share = exporter.shareIntent(listOf(out), "video/mp4")
            @Suppress("DEPRECATION") val inner = share.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            @Suppress("DEPRECATION") val stream = inner.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
            QaRecorder.record("Share (FileProvider URI)", "MEASURED", "uri" to stream.toString())
            assertThat(stream.scheme).isEqualTo("content")
            ctx.contentResolver.openInputStream(stream)!!.use { assertThat(it.read()).isAtLeast(0) }
        } finally {
            db.close()
        }
    }

    private suspend fun runDemoPipeline(db: AppDatabase): ProjectBundle {
        val files = ProjectFiles(ctx)
        val projects = ProjectRepository(db, files)
        val http = OkHttpClient()
        val media = MediaRepository(ctx, db.assets(), files, SafeFetcher(http))
        val settings = InMemorySettingsRepository(AppSettings(onboardingDone = true, voiceEnabled = true))
        val client = ClaudeClient(http, InMemorySecretStore())
        val gateway = AiGateway(client, ModelResolver(client))
        val orchestrator = PipelineOrchestrator(
            projects, db.generations(), media, ProductExtractionService(SafeFetcher(http)),
            AiEngineProvider(settings, client, gateway, PromptRepository(ctx, db.prompts())), settings, VisualService(media),
            ShortsService(media, AndroidTtsProvider(ctx), files), gateway, Notifier(ctx),
        )
        val product = DemoData.product()
        val project = projects.createProject(product, "v1")
        media.saveBitmap(project.id, DemoData.illustration(), AssetKind.ORIGINAL, "demo", copyright = CopyrightType.APP_GENERATED)
        val gen = GenerationEntity(
            "gen-device-qa", project.id, product.affiliateUrl, GenerationState.QUEUED,
            AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(StepStatus.serializer()), orchestrator.initialSteps()),
            AppJson.encodeToString(PipelineOptions.serializer(), PipelineOptions()), "", "v1", null, null, "[]", null, null, 0, 0, 0, null,
        )
        db.generations().upsert(gen)
        orchestrator.run(gen.id)
        assertThat(db.generations().get(gen.id)!!.state).isAnyOf(GenerationState.SUCCEEDED, GenerationState.PARTIAL)
        return projects.observeBundleOnce(project.id)!!
    }

    private data class Probe(val videoMime: String?, val width: Int, val height: Int, val fps: Double, val videoMs: Long, val audioMime: String?, val audioMs: Long, val rotation: Int, val minFrameVariance: Double)

    private fun probe(file: File): Probe {
        val ex = MediaExtractor().apply { setDataSource(file.absolutePath) }
        var vMime: String? = null; var aMime: String? = null; var w = 0; var h = 0; var vUs = 0L; var aUs = 0L; var vTrack = -1
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i); val mime = f.getString(MediaFormat.KEY_MIME)!!
            if (mime.startsWith("video/")) { vMime = mime; w = f.getInteger(MediaFormat.KEY_WIDTH); h = f.getInteger(MediaFormat.KEY_HEIGHT); vUs = f.getLong(MediaFormat.KEY_DURATION); vTrack = i }
            if (mime.startsWith("audio/")) { aMime = mime; aUs = f.getLong(MediaFormat.KEY_DURATION) }
        }
        var frames = 0
        if (vTrack >= 0) { ex.selectTrack(vTrack); while (ex.sampleTime >= 0) { frames++; ex.advance() } }
        ex.release()
        val mmr = MediaMetadataRetriever().apply { setDataSource(file.absolutePath) }
        val rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val minVar = listOf(0.1, 0.35, 0.6, 0.85).mapIndexed { i, f ->
            val bmp = mmr.getFrameAtTime((vUs * f).toLong(), MediaMetadataRetriever.OPTION_CLOSEST)!!
            QaRecorder.saveBitmap("render_frame_$i", bmp)
            QaRecorder.variance(bmp).also { bmp.recycle() }
        }.min()
        mmr.release()
        return Probe(vMime, w, h, frames * 1_000_000.0 / vUs, vUs / 1000, aMime, aUs / 1000, rotation, minVar)
    }

    private companion object { const val SENTENCE = "이 제품의 핵심 특징 세 가지를 빠르게 살펴보겠습니다." }
}
