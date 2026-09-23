package com.shoppingconnect.aistudio.domain.model

import com.shoppingconnect.aistudio.core.common.newId
import kotlinx.serialization.Serializable

enum class TransitionType(val label: String) {
    NONE("없음"), FADE("페이드"), CROSS_FADE("크로스 페이드"), SLIDE_LEFT("슬라이드 ←"), SLIDE_RIGHT("슬라이드 →"),
    SLIDE_UP("슬라이드 ↑"), SLIDE_DOWN("슬라이드 ↓"), ZOOM_IN("줌 인"), ZOOM_OUT("줌 아웃"), BLUR("블러"),
    FLASH("플래시"), WIPE("와이프"), SPIN_LIGHT("라이트 스핀"),
}

@Serializable
data class Transition(val type: TransitionType = TransitionType.NONE, val durationMs: Long = 300) {
    val clampedMs: Long get() = durationMs.coerceIn(100, 2000)
}

enum class MotionEffect(val label: String) { NONE("없음"), KEN_BURNS_IN("켄 번즈 인"), KEN_BURNS_OUT("켄 번즈 아웃"), PAN_LEFT("팬 ←"), PAN_RIGHT("팬 →"), PARALLAX("패럴랙스"), PULSE("펄스") }

enum class FitMode { FIT, FILL }

enum class TextAnimation(val label: String) { NONE("없음"), FADE("페이드"), SLIDE("슬라이드"), POP("팝"), SCALE("스케일"), TYPEWRITER("타자기"), BOUNCE("바운스"), MASK_REVEAL("마스크 리빌") }

enum class TextStylePreset(val label: String) { BOLD_COMMERCE("Bold Commerce"), MINIMAL_CAPTION("Minimal Caption"), NEWS("News Style"), PRODUCT_REVIEW("Product Review"), PREMIUM("Premium"), CUTE("Cute"), TECH("Tech") }

enum class SubtitleStyle(val label: String) { CLASSIC("Classic"), BOLD("Bold"), MINIMAL("Minimal"), KOREAN_SHORTS("Korean Shorts"), HIGHLIGHT("Highlight") }

enum class BgmMood(val label: String) { NONE("없음"), UPBEAT("Upbeat"), MINIMAL("Minimal"), TECH("Tech"), CUTE("Cute"), PREMIUM("Premium"), CALM("Calm") }

enum class SfxType(val label: String) { POP("Pop"), WHOOSH("Whoosh"), TAP("Tap"), TRANSITION("Transition"), SUCCESS("Success") }

enum class VoicePreset(val label: String, val pitch: Float, val rate: Float) {
    BRIGHT_MALE("밝은 남성", 0.95f, 1.08f), CALM_MALE("차분한 남성", 0.85f, 0.95f),
    BRIGHT_FEMALE("밝은 여성", 1.15f, 1.08f), CALM_FEMALE("차분한 여성", 1.05f, 0.95f),
    NARRATION("내레이션", 1.0f, 1.0f), COMMERCE("커머스", 1.05f, 1.15f),
}

@Serializable
data class VoiceSettings(
    val enabled: Boolean = true,
    val provider: String = "android_tts",
    val preset: VoicePreset = VoicePreset.NARRATION,
    val voiceName: String? = null,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val sentenceGapMs: Long = 250,
    val volume: Float = 1.0f,
)

@Serializable
data class TrackMix(val volume: Float = 1f, val muted: Boolean = false, val solo: Boolean = false, val fadeInMs: Long = 0, val fadeOutMs: Long = 0, val pan: Float = 0f)

@Serializable
data class AudioMix(
    val voice: TrackMix = TrackMix(1f),
    val music: TrackMix = TrackMix(0.35f, fadeInMs = 800, fadeOutMs = 1500),
    val sfx: TrackMix = TrackMix(0.7f),
    val ducking: Boolean = true,
    val duckLevel: Float = 0.35f,
    val limiter: Boolean = true,
)

/** A visual clip on the main (video/image) track. */
@Serializable
data class Scene(
    val id: String = newId(),
    val purpose: String = "",
    val mediaAssetId: String? = null,
    val mediaPath: String? = null,
    val isVideo: Boolean = false,
    val durationMs: Long = 3000,
    val narration: String = "",
    val caption: String = "",
    val transitionIn: Transition = Transition(),
    val motion: MotionEffect = MotionEffect.KEN_BURNS_IN,
    val fit: FitMode = FitMode.FILL,
    val rotation: Int = 0,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val trimStartMs: Long = 0,
    val backgroundArgb: Int = 0xFF101014.toInt(),
)

@Serializable
data class TextClip(
    val id: String = newId(),
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val style: TextStylePreset = TextStylePreset.BOLD_COMMERCE,
    val enter: TextAnimation = TextAnimation.POP,
    val exit: TextAnimation = TextAnimation.FADE,
    val yFraction: Float = 0.22f,
    val sizeSp: Float = 64f,
    val highlight: String? = null,
    val sceneId: String? = null,
)

@Serializable
data class SubtitleCue(val id: String = newId(), val text: String, val startMs: Long, val endMs: Long, val highlights: List<String> = emptyList())

@Serializable
data class AudioClip(
    val id: String = newId(),
    val path: String? = null,
    val startMs: Long = 0,
    val durationMs: Long = 0,
    val trimStartMs: Long = 0,
    val volume: Float = 1f,
    val label: String = "",
    val sceneId: String? = null,
    val sfx: SfxType? = null,
)

@Serializable
data class StickerClip(val id: String = newId(), val emoji: String, val startMs: Long, val endMs: Long, val xFraction: Float = 0.8f, val yFraction: Float = 0.35f, val sizePx: Int = 140)

enum class ShortsTemplateId(val label: String) {
    CLEAN_PRODUCT("Clean Product"), DYNAMIC("Dynamic"), MINIMAL("Minimal"), DARK_PREMIUM("Dark Premium"), COMMERCE("Commerce"),
    FLASH_COMMERCE("Flash Commerce"), PREMIUM_SHOWCASE("Premium Showcase"), FAST_REVIEW("Fast Review"), THREE_REASONS("3 Reasons"),
    BEFORE_BUY("Before Buy"), FEATURE_HIGHLIGHTS("Feature Highlights"), PROBLEM_SOLUTION("Problem Solution"), MINIMAL_REEL("Minimal Reel"),
}

enum class ExportProfile(val label: String, val maxDurationSec: Int) { YOUTUBE_SHORTS("YouTube Shorts", 180), INSTAGRAM_REELS("Instagram Reels", 180), TIKTOK("TikTok", 600), GENERIC("Generic 9:16", 600) }

enum class RenderQuality(val label: String, val bitsPerPixel: Float) { FAST("Fast", 0.06f), BALANCED("Balanced", 0.1f), QUALITY("Quality", 0.16f) }

enum class VideoResolution(val label: String, val width: Int, val height: Int) { P720("720p", 720, 1280), P1080("1080p", 1080, 1920), P1440("1440p", 1440, 2560) }

@Serializable
data class RenderSettings(
    val resolution: VideoResolution = VideoResolution.P1080,
    val fps: Int = 30,
    val quality: RenderQuality = RenderQuality.BALANCED,
    val hevc: Boolean = false,
    val exportProfile: ExportProfile = ExportProfile.GENERIC,
)

@Serializable
data class ShortTimeline(
    val template: ShortsTemplateId = ShortsTemplateId.CLEAN_PRODUCT,
    val targetDurationSec: Int = 30,
    val scenes: List<Scene> = emptyList(),
    val texts: List<TextClip> = emptyList(),
    val subtitles: List<SubtitleCue> = emptyList(),
    val subtitleStyle: SubtitleStyle = SubtitleStyle.KOREAN_SHORTS,
    val subtitlesEnabled: Boolean = true,
    val voiceClips: List<AudioClip> = emptyList(),
    val music: AudioClip? = null,
    val bgmMood: BgmMood = BgmMood.UPBEAT,
    val sfx: List<AudioClip> = emptyList(),
    val stickers: List<StickerClip> = emptyList(),
    val voice: VoiceSettings = VoiceSettings(),
    val mix: AudioMix = AudioMix(),
    val render: RenderSettings = RenderSettings(),
    val hook: String = "",
    val hookCandidates: List<HookCandidate> = emptyList(),
    val watermark: String? = null,
    val showSafeZone: Boolean = true,
) {
    val durationMs: Long get() = sceneStarts().lastOrNull()?.let { it + scenes.last().durationMs } ?: 0L

    /** Start time of each scene. Transitions overlap the previous scene (cross-fade style). */
    fun sceneStarts(): List<Long> {
        val out = ArrayList<Long>(scenes.size)
        var t = 0L
        scenes.forEachIndexed { i, s ->
            if (i > 0) {
                val overlap = if (s.transitionIn.type == TransitionType.NONE) 0 else s.transitionIn.clampedMs.coerceAtMost(minOf(s.durationMs, scenes[i - 1].durationMs) / 2)
                t -= overlap
            }
            out += t
            t += s.durationMs
        }
        return out
    }

    fun sceneAt(timeMs: Long): Int {
        val starts = sceneStarts()
        for (i in starts.indices.reversed()) if (timeMs >= starts[i]) return i
        return 0
    }
}

enum class HookType(val label: String) { PROBLEM("문제형"), CURIOSITY("호기심형"), COMPARISON("비교형"), BENEFIT("핵심 이점형"), QUESTION("질문형") }

@Serializable
data class HookCandidate(val text: String, val type: HookType)

@Serializable
data class ThumbnailSpec(
    val title: String = "",
    val hook: String = "",
    val badge: String = "",
    val imageAssetId: String? = null,
    val layout: Int = 0,
    val gradientTopArgb: Int = 0xFF5B4CF0.toInt(),
    val gradientBottomArgb: Int = 0xFF101014.toInt(),
    val sticker: String = "",
    val candidates: List<String> = emptyList(),
)
