package com.shoppingconnect.aistudio.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.domain.model.AspectRatio
import com.shoppingconnect.aistudio.domain.model.BlogCard
import com.shoppingconnect.aistudio.domain.model.CardStyle
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.CardType
import com.shoppingconnect.aistudio.domain.model.MotionEffect
import com.shoppingconnect.aistudio.domain.model.Scene
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.domain.model.Spec
import com.shoppingconnect.aistudio.domain.model.StickerClip
import com.shoppingconnect.aistudio.domain.model.SubtitleCue
import com.shoppingconnect.aistudio.domain.model.SubtitleStyle
import com.shoppingconnect.aistudio.domain.model.TextAnimation
import com.shoppingconnect.aistudio.domain.model.TextClip
import com.shoppingconnect.aistudio.domain.model.TextStylePreset
import com.shoppingconnect.aistudio.domain.model.ThumbnailSpec
import com.shoppingconnect.aistudio.domain.model.Transition
import com.shoppingconnect.aistudio.domain.model.TransitionType
import com.shoppingconnect.aistudio.media.audio.Pcm
import com.shoppingconnect.aistudio.media.audio.WavIO
import com.shoppingconnect.aistudio.media.shorts.ThumbnailRenderer
import com.shoppingconnect.aistudio.media.video.FrameMediaSource
import com.shoppingconnect.aistudio.media.video.FrameRenderer
import com.shoppingconnect.aistudio.visual.CardRenderer
import com.shoppingconnect.aistudio.visual.ColorTools
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Renderers run with Robolectric native graphics (real Canvas/StaticLayout). */
@RunWith(RobolectricTestRunner::class)
class RenderersTest {
    private fun image(w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 80, 60)) }
    private val longKo = "아주 긴 한국어 제목이 카드 밖으로 잘리지 않도록 자동으로 글자 크기를 줄이고 말줄임을 적용하는지 확인하는 문장입니다 🎧✨"

    @Test fun rendersEveryCardTypeAndAspectWithoutCrash() {
        CardType.entries.forEach { type ->
            AspectRatio.entries.forEach { aspect ->
                val card = BlogCard(type = type, title = longKo, subtitle = longKo, items = List(5) { longKo }, rows = List(8) { Spec("항목 $it", longKo) }, cta = "상품 보러가기",
                    style = CardStyle(preset = CardStylePreset.entries[(type.ordinal + aspect.ordinal) % CardStylePreset.entries.size], aspect = aspect))
                val bmp = CardRenderer.render(card, 1080, if (type.ordinal % 2 == 0) image(1200, 800) else image(600, 1400), null)
                assertThat(bmp.width).isEqualTo(1080)
                assertThat(bmp.height).isEqualTo(aspect.heightFor(1080))
                bmp.recycle()
            }
        }
    }

    @Test fun highResExportSizes() {
        listOf(1440, 2160).forEach { w -> assertThat(CardRenderer.render(BlogCard(type = CardType.HERO, title = "t"), w, null, null).width).isEqualTo(w) }
    }

    @Test fun textFittingRespectsBounds() {
        val l = CardRenderer.fit(longKo.repeat(4), 600, 200f, 80f, 20f, 3, Color.BLACK, true, com.shoppingconnect.aistudio.domain.model.TextAlign.START)
        assertThat(l.lineCount).isAtMost(3)
    }

    @Test fun contrastIsEnforced() {
        val t = CardRenderer.theme(CardStylePreset.CLEAN, null, BlogCard(type = CardType.HERO, title = "x", style = CardStyle(backgroundArgb = Color.WHITE, textArgb = 0xFFF0F0F0.toInt())))
        assertThat(ColorTools.contrast(t.bg, t.text)).isAtLeast(4.5)
    }

    @Test fun thumbnailLayouts() {
        (0 until ThumbnailRenderer.LAYOUTS).forEach { l ->
            val b = ThumbnailRenderer.render(ThumbnailSpec(title = longKo, hook = "짧은 훅", badge = "BEST", sticker = "🔥", layout = l), image(1000, 1000))
            assertThat(b.width).isEqualTo(1080); assertThat(b.height).isEqualTo(1920)
        }
    }

    private class FakeSource(val bmps: Map<String, Bitmap?>) : FrameMediaSource {
        override fun image(scene: Scene) = bmps[scene.id]
        override fun blurred(scene: Scene) = bmps[scene.id]?.let { Bitmap.createScaledBitmap(it, 8, 8, true) }
        override fun videoFrame(scene: Scene, localMs: Long) = null
    }

    @Test fun frameRendererHandlesAllTransitionsMotionsAndText() {
        val scenes = TransitionType.entries.mapIndexed { i, tr ->
            Scene(id = "s$i", durationMs = 1500, transitionIn = Transition(if (i == 0) TransitionType.NONE else tr, 500), motion = MotionEffect.entries[i % MotionEffect.entries.size])
        }
        val src = FakeSource(scenes.associate { it.id to if (it.id == "s3") null else if (it.id.hashCode() % 2 == 0) image(1920, 1080) else image(800, 1600) })
        val t0 = ShortTimeline(scenes = scenes)
        val texts = TextStylePreset.entries.mapIndexed { i, st -> TextClip(text = longKo, startMs = i * 800L, endMs = i * 800L + 1200, style = st, enter = TextAnimation.entries[i % TextAnimation.entries.size], exit = TextAnimation.entries[(i + 3) % TextAnimation.entries.size], highlight = "한국어") }
        SubtitleStyle.entries.forEach { style ->
            val t = t0.copy(texts = texts, subtitleStyle = style, subtitles = listOf(SubtitleCue(text = "자막 😀 테스트", startMs = 0, endMs = t0.durationMs, highlights = listOf("자막"))),
                stickers = listOf(StickerClip(emoji = "🔥", startMs = 0, endMs = 3000)), watermark = "@me")
            val w = 360; val h = 640
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val r = FrameRenderer(t, w, h, src)
            var ms = 0L
            while (ms < t.durationMs) { r.draw(Canvas(bmp), ms, preview = ms % 2 == 0L); ms += 97 }
        }
    }

    @Test fun oneAndManyScenes() {
        listOf(1, 20).forEach { n ->
            val t = ShortTimeline(scenes = List(n) { Scene(id = "s$it", durationMs = 800) })
            val bmp = Bitmap.createBitmap(180, 320, Bitmap.Config.ARGB_8888)
            FrameRenderer(t, 180, 320, FakeSource(emptyMap())).draw(Canvas(bmp), t.durationMs - 1)
        }
    }

    @Test fun wavRoundTrip() {
        val f = File.createTempFile("wavtest", ".wav")
        val pcm = Pcm(FloatArray(4410) { (it % 100) / 100f - 0.5f })
        WavIO.write(f, pcm)
        val back = WavIO.read(f)
        assertThat(back.samples.size).isEqualTo(4410)
        assertThat(back.durationMs).isEqualTo(100)
        f.delete()
    }
}
