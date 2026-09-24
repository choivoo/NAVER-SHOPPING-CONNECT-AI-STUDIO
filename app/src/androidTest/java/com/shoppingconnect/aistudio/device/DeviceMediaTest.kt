package com.shoppingconnect.aistudio.device

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.ai.DemoAiEngine
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.AspectRatio
import com.shoppingconnect.aistudio.domain.model.BlogCard
import com.shoppingconnect.aistudio.domain.model.CardStyle
import com.shoppingconnect.aistudio.domain.model.CardType
import com.shoppingconnect.aistudio.domain.model.RenderSettings
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import com.shoppingconnect.aistudio.domain.model.VideoResolution
import com.shoppingconnect.aistudio.domain.model.VoiceSettings
import com.shoppingconnect.aistudio.media.export.MediaExporter
import com.shoppingconnect.aistudio.media.shorts.AssetRefForShorts
import com.shoppingconnect.aistudio.media.shorts.AutoEditor
import com.shoppingconnect.aistudio.media.shorts.ShortsTemplates
import com.shoppingconnect.aistudio.media.shorts.VoiceSync
import com.shoppingconnect.aistudio.media.subtitle.KeywordHighlighter
import com.shoppingconnect.aistudio.media.tts.AndroidTtsProvider
import com.shoppingconnect.aistudio.media.video.CodecSupport
import com.shoppingconnect.aistudio.media.video.ShortsRenderer
import com.shoppingconnect.aistudio.pipeline.DemoData
import com.shoppingconnect.aistudio.visual.CardRenderer
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream

/** Real hardware checks: device info, card rendering, Android TTS, MediaCodec MP4 render, MediaStore, share URI. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DeviceMediaTest {
    private val ctx get() = QaLog.target

    @Test fun t01_deviceInfo() {
        val dm = ctx.resources.displayMetrics
        val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
            .filter { i -> i.supportedTypes.any { it.startsWith("video/avc") || it.startsWith("video/hevc") } }
            .joinToString { "${it.name}${if (it.isHardwareAccelerated) "(hw)" else "(sw)"}" }
        QaLog.row("Device", "INFO", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${dm.widthPixels}x${dm.heightPixels} @${dm.densityDpi}dpi · ABI ${Build.SUPPORTED_ABIS.joinToString()}")
        QaLog.row("Video encoders", "INFO", encoders)
    }

    private fun card(type: CardType, aspect: AspectRatio) = BlogCard(
        type = type, title = "노이즈 캔슬링 무선 이어폰 핵심 정리 🎧", subtitle = "공개된 상품 정보를 기준으로 살펴본 특징",
        items = listOf("블루투스 5.3 연결", "최대 8시간 재생 (케이스 포함 30시간)", "IPX4 생활 방수"),
        rows = DemoData.product().specifications, cta = "상품 보러가기", style = CardStyle(aspect = aspect),
    )

    @Test fun t02_cardRender1080And2160() {
        val img = DemoData.illustration()
        listOf(CardType.HERO to AspectRatio.PORTRAIT_4_5, CardType.FEATURE to AspectRatio.SQUARE, CardType.SPEC to AspectRatio.PORTRAIT_4_5, CardType.CTA to AspectRatio.WIDE_16_9).forEach { (type, aspect) ->
            listOf(1080, 2160).forEach { w ->
                val t0 = System.currentTimeMillis()
                val bmp = CardRenderer.render(card(type, aspect), w, img, null)
                val ms = System.currentTimeMillis() - t0
                assertThat(bmp.width).isEqualTo(w)
                val f = File(QaLog.dir, "card_${type.name.lowercase()}_$w.jpg")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                QaLog.row("Card ${type.name} ${w}px", "PASS", "${bmp.width}x${bmp.height} in ${ms}ms, ${f.length() / 1024}KB → ${f.name}")
                bmp.recycle()
            }
        }
    }

    @Test fun t03_androidTtsKorean() = runBlocking {
        val tts = AndroidTtsProvider(ctx)
        val available = tts.isAvailable()
        if (!available) {
            QaLog.row("TTS Korean", "NOT AVAILABLE", "Korean voice data not installed — app shows setup guidance instead of crashing")
            assumeTrue("Korean TTS not installed", false)
        }
        val voices = tts.voices()
        val t0 = System.currentTimeMillis()
        val r = tts.synthesize("이 제품의 핵심 특징 세 가지를 빠르게 살펴보겠습니다.", VoiceSettings(), File(QaLog.dir, "tts").apply { mkdirs() })
        val ms = System.currentTimeMillis() - t0
        assertThat(r.durationMs).isGreaterThan(1000)
        QaLog.row("TTS Korean", "PASS", "voices=${voices.size} (${voices.take(3).joinToString { it.name }}), audio ${r.durationMs}ms, synth ${ms}ms")
        tts.shutdown()
    }

    private fun renderDemo(res: VideoResolution, hevc: Boolean, name: String): File = runBlocking {
        val product = DemoData.product()
        val imgFile = File(QaLog.dir, "demo_product.jpg").also { f -> FileOutputStream(f).use { DemoData.illustration().compress(Bitmap.CompressFormat.JPEG, 92, it) } }
        val cardFile = File(QaLog.dir, "demo_cta.jpg").also { f -> FileOutputStream(f).use { CardRenderer.render(card(CardType.CTA, AspectRatio.STORY_9_16), 1080, null, null).compress(Bitmap.CompressFormat.JPEG, 92, it) } }
        val draft = DemoAiEngine().shortform(product, Article(), 30, ShortsTemplateId.DYNAMIC, UsageStatus.INTRO_ONLY)
        val template = ShortsTemplates.of(ShortsTemplateId.DYNAMIC)
        var tl: ShortTimeline = AutoEditor.build(
            draft, product, listOf(AssetRefForShorts("img", imgFile.absolutePath)), mapOf("CTA" to AssetRefForShorts("cta", cardFile.absolutePath)),
            template, 30, VoiceSettings(), RenderSettings(resolution = res, fps = 30, hevc = hevc), true, null,
        )
        val tts = AndroidTtsProvider(ctx)
        if (tts.isAvailable()) {
            val voices = LinkedHashMap<String, Pair<String, Long>>()
            tl.scenes.filter { it.narration.isNotBlank() }.forEach { s ->
                val r = tts.synthesize(s.narration, tl.voice, File(QaLog.dir, "tts").apply { mkdirs() })
                voices[s.id] = r.file.absolutePath to r.durationMs
            }
            tl = VoiceSync.apply(tl, template, voices, KeywordHighlighter.factTokens(product))
        }
        val out = File(QaLog.dir, "$name.mp4")
        val choice = CodecSupport.choose(hevc, res.width, res.height, 30)
        val t0 = System.currentTimeMillis()
        val stats = ShortsRenderer(tl) { it.mediaPath }.render(out, onProgress = {}, isCancelled = { false })
        val wall = System.currentTimeMillis() - t0

        // Verify the container, not just the file's existence.
        val ex = MediaExtractor().apply { setDataSource(out.absolutePath) }
        val formats = (0 until ex.trackCount).map { ex.getTrackFormat(it) }
        ex.release()
        val video = formats.first { it.getString(MediaFormat.KEY_MIME)!!.startsWith("video/") }
        val audio = formats.firstOrNull { it.getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
        val mmr = MediaMetadataRetriever().apply { setDataSource(out.absolutePath) }
        val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        val rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        val black = listOf(0.1, 0.5, 0.9).count { f -> mmr.getFrameAtTime((durMs * 1000 * f).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { meanLuma(it) < 8 } ?: true }
        mmr.release()

        assertThat(video.getInteger(MediaFormat.KEY_WIDTH)).isEqualTo(stats.width)
        assertThat(video.getInteger(MediaFormat.KEY_HEIGHT)).isEqualTo(stats.height)
        assertThat(audio).isNotNull()
        assertThat(kotlin.math.abs(durMs - tl.durationMs)).isLessThan(700)
        assertThat(black).isEqualTo(0)
        QaLog.row(
            "Render $name", "PASS",
            "${stats.width}x${stats.height} ${video.getString(MediaFormat.KEY_MIME)} via ${choice.codecName}${if (choice.hardware) " (hw)" else " (sw)"} · 30fps · ${stats.frames} frames · " +
                "video ${durMs}ms (timeline ${tl.durationMs}ms) · audio ${audio?.getString(MediaFormat.KEY_MIME)} · rotation $rotation · " +
                "render ${wall}ms (${"%.2f".format(wall / durMs.toDouble())}x realtime) · ${out.length() / 1024}KB · TTS=${tl.voiceClips.isNotEmpty()} · black frames=$black",
        )
        out
    }

    private fun meanLuma(b: Bitmap): Double {
        val s = Bitmap.createScaledBitmap(b, 32, 56, true)
        var sum = 0.0
        for (x in 0 until s.width) for (y in 0 until s.height) { val c = s.getPixel(x, y); sum += 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c) }
        return sum / (s.width * s.height)
    }

    @Test fun t04_render1080pH264() { renderDemo(VideoResolution.P1080, hevc = false, name = "shorts_1080p_h264") }

    @Test fun t05_render720pHevcIfSupported() {
        val hevcAvailable = runCatching { CodecSupport.choose(true, 720, 1280, 30).mime == MediaFormat.MIMETYPE_VIDEO_HEVC }.getOrDefault(false)
        if (!hevcAvailable) { QaLog.row("Render HEVC", "NOT AVAILABLE", "no HEVC surface encoder → app falls back to H.264"); assumeTrue(false) }
        renderDemo(VideoResolution.P720, hevc = true, name = "shorts_720p_hevc")
    }

    @Test fun t06_mediaStoreSaveAndShareUri() = runBlocking {
        val video = File(QaLog.dir, "shorts_1080p_h264.mp4")
        assumeTrue("render test must run first", video.exists())
        val exporter = MediaExporter(ctx)
        val uri = exporter.saveVideo(video, "AIStudio_QA_${System.currentTimeMillis()}.mp4")
        val found = ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getLong(1) else null
        }
        assertThat(found).isNotNull()
        QaLog.row("Gallery export (MediaStore)", "PASS", "uri=$uri path=${found!!.first} size=${found.second / 1024}KB (QA copy deleted afterwards)")
        ctx.contentResolver.delete(uri, null, null)

        val share = exporter.shareIntent(listOf(video), "video/mp4")
        @Suppress("DEPRECATION") val inner = share.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION") val stream = inner.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!
        assertThat(stream.scheme).isEqualTo("content")
        QaLog.row("Share URI", "PASS", "content URI via FileProvider: ${stream.authority}; Sharesheet UI itself must be opened manually")
    }

    @Test fun t07_ttsMissingIsHandled() = runBlocking {
        // Unknown voice name must not crash synthesis (falls back to installed Korean voice / NotConfigured error).
        val tts = AndroidTtsProvider(ctx)
        val r = runCatching { tts.synthesize("테스트", VoiceSettings(voiceName = "does-not-exist"), File(QaLog.dir, "tts")) }
        val e = r.exceptionOrNull()
        assertThat(e == null || e is AppException).isTrue()
        QaLog.row("TTS invalid voice", "PASS", if (e == null) "fell back to installed voice" else "handled: ${(e as AppException).kind}")
    }
}
