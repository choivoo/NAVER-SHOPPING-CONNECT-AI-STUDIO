package com.shoppingconnect.aistudio.media.shorts

import com.shoppingconnect.aistudio.domain.model.BgmMood
import com.shoppingconnect.aistudio.domain.model.FitMode
import com.shoppingconnect.aistudio.domain.model.MotionEffect
import com.shoppingconnect.aistudio.domain.model.SfxType
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.SubtitleStyle
import com.shoppingconnect.aistudio.domain.model.TextAnimation
import com.shoppingconnect.aistudio.domain.model.TextStylePreset
import com.shoppingconnect.aistudio.domain.model.TransitionType
import kotlinx.serialization.Serializable

/** Declarative shorts template (Template Engine 2.0). New templates = new data, no code changes. */
@Serializable
data class ShortsTemplate(
    val id: ShortsTemplateId,
    val description: String,
    val transition: TransitionType,
    val accentTransition: TransitionType,
    val transitionMs: Long,
    val motions: List<MotionEffect>,
    val textStyle: TextStylePreset,
    val textEnter: TextAnimation,
    val textExit: TextAnimation,
    val subtitleStyle: SubtitleStyle,
    val bgm: BgmMood,
    val transitionSfx: SfxType?,
    val hookSfx: SfxType?,
    val backgroundArgb: Int,
    val fit: FitMode = FitMode.FILL,
    val hookSticker: String? = null,
    val textY: Float = 0.2f,
)

object ShortsTemplates {
    val all: List<ShortsTemplate> = listOf(
        ShortsTemplate(ShortsTemplateId.CLEAN_PRODUCT, "화이트 기반의 깔끔한 제품 소개", TransitionType.CROSS_FADE, TransitionType.SLIDE_LEFT, 350,
            listOf(MotionEffect.KEN_BURNS_IN, MotionEffect.PAN_LEFT, MotionEffect.KEN_BURNS_OUT), TextStylePreset.PRODUCT_REVIEW, TextAnimation.SLIDE, TextAnimation.FADE,
            SubtitleStyle.CLASSIC, BgmMood.MINIMAL, null, SfxType.TAP, 0xFFF4F5F9.toInt(), FitMode.FIT),
        ShortsTemplate(ShortsTemplateId.DYNAMIC, "빠른 줌/슬라이드", TransitionType.SLIDE_LEFT, TransitionType.ZOOM_IN, 250,
            listOf(MotionEffect.KEN_BURNS_IN, MotionEffect.PULSE, MotionEffect.PAN_RIGHT), TextStylePreset.BOLD_COMMERCE, TextAnimation.POP, TextAnimation.SCALE,
            SubtitleStyle.KOREAN_SHORTS, BgmMood.UPBEAT, SfxType.WHOOSH, SfxType.POP, 0xFF101014.toInt()),
        ShortsTemplate(ShortsTemplateId.MINIMAL, "텍스트 최소, 여백 중심", TransitionType.FADE, TransitionType.FADE, 500,
            listOf(MotionEffect.KEN_BURNS_IN, MotionEffect.KEN_BURNS_OUT), TextStylePreset.MINIMAL_CAPTION, TextAnimation.FADE, TextAnimation.FADE,
            SubtitleStyle.MINIMAL, BgmMood.CALM, null, null, 0xFF1A1A1F.toInt()),
        ShortsTemplate(ShortsTemplateId.DARK_PREMIUM, "어두운 배경의 고급스러운 연출", TransitionType.CROSS_FADE, TransitionType.ZOOM_OUT, 600,
            listOf(MotionEffect.KEN_BURNS_IN, MotionEffect.PARALLAX), TextStylePreset.PREMIUM, TextAnimation.MASK_REVEAL, TextAnimation.FADE,
            SubtitleStyle.MINIMAL, BgmMood.PREMIUM, null, null, 0xFF0E0C0A.toInt()),
        ShortsTemplate(ShortsTemplateId.COMMERCE, "가격/특징 카드 중심", TransitionType.SLIDE_UP, TransitionType.FLASH, 300,
            listOf(MotionEffect.PULSE, MotionEffect.KEN_BURNS_IN), TextStylePreset.BOLD_COMMERCE, TextAnimation.POP, TextAnimation.FADE,
            SubtitleStyle.HIGHLIGHT, BgmMood.UPBEAT, SfxType.WHOOSH, SfxType.POP, 0xFF1B1030.toInt(), hookSticker = "🔥"),
        ShortsTemplate(ShortsTemplateId.FLASH_COMMERCE, "플래시 전환의 빠른 커머스", TransitionType.FLASH, TransitionType.ZOOM_IN, 220,
            listOf(MotionEffect.PULSE, MotionEffect.KEN_BURNS_IN), TextStylePreset.BOLD_COMMERCE, TextAnimation.POP, TextAnimation.SCALE,
            SubtitleStyle.KOREAN_SHORTS, BgmMood.UPBEAT, SfxType.TRANSITION, SfxType.POP, 0xFF140F1F.toInt(), hookSticker = "⚡"),
        ShortsTemplate(ShortsTemplateId.PREMIUM_SHOWCASE, "제품 쇼케이스", TransitionType.CROSS_FADE, TransitionType.WIPE, 700,
            listOf(MotionEffect.PARALLAX, MotionEffect.KEN_BURNS_OUT), TextStylePreset.PREMIUM, TextAnimation.FADE, TextAnimation.FADE,
            SubtitleStyle.MINIMAL, BgmMood.PREMIUM, null, null, 0xFF0E0C0A.toInt()),
        ShortsTemplate(ShortsTemplateId.FAST_REVIEW, "빠른 정보 리뷰형", TransitionType.SLIDE_LEFT, TransitionType.SLIDE_UP, 240,
            listOf(MotionEffect.PAN_LEFT, MotionEffect.PAN_RIGHT), TextStylePreset.NEWS, TextAnimation.SLIDE, TextAnimation.SLIDE,
            SubtitleStyle.BOLD, BgmMood.TECH, SfxType.WHOOSH, SfxType.TAP, 0xFF0B1220.toInt()),
        ShortsTemplate(ShortsTemplateId.THREE_REASONS, "살 만한 이유 3가지", TransitionType.ZOOM_IN, TransitionType.SLIDE_LEFT, 300,
            listOf(MotionEffect.KEN_BURNS_IN), TextStylePreset.BOLD_COMMERCE, TextAnimation.POP, TextAnimation.FADE,
            SubtitleStyle.KOREAN_SHORTS, BgmMood.UPBEAT, SfxType.POP, SfxType.POP, 0xFF101014.toInt()),
        ShortsTemplate(ShortsTemplateId.BEFORE_BUY, "구매 전 체크 포인트", TransitionType.SLIDE_UP, TransitionType.FADE, 300,
            listOf(MotionEffect.KEN_BURNS_OUT, MotionEffect.PAN_LEFT), TextStylePreset.PRODUCT_REVIEW, TextAnimation.SLIDE, TextAnimation.FADE,
            SubtitleStyle.CLASSIC, BgmMood.MINIMAL, SfxType.TAP, SfxType.TAP, 0xFFF4F5F9.toInt()),
        ShortsTemplate(ShortsTemplateId.FEATURE_HIGHLIGHTS, "기능 하이라이트", TransitionType.WIPE, TransitionType.ZOOM_IN, 350,
            listOf(MotionEffect.KEN_BURNS_IN, MotionEffect.PULSE), TextStylePreset.TECH, TextAnimation.TYPEWRITER, TextAnimation.FADE,
            SubtitleStyle.BOLD, BgmMood.TECH, SfxType.WHOOSH, SfxType.TAP, 0xFF0B1220.toInt()),
        ShortsTemplate(ShortsTemplateId.PROBLEM_SOLUTION, "문제 → 해결 구성", TransitionType.CROSS_FADE, TransitionType.FLASH, 400,
            listOf(MotionEffect.KEN_BURNS_IN, MotionEffect.KEN_BURNS_OUT), TextStylePreset.NEWS, TextAnimation.SLIDE, TextAnimation.FADE,
            SubtitleStyle.KOREAN_SHORTS, BgmMood.CALM, null, SfxType.POP, 0xFF141A24.toInt()),
        ShortsTemplate(ShortsTemplateId.MINIMAL_REEL, "미니멀 릴스", TransitionType.FADE, TransitionType.CROSS_FADE, 450,
            listOf(MotionEffect.PARALLAX), TextStylePreset.MINIMAL_CAPTION, TextAnimation.FADE, TextAnimation.FADE,
            SubtitleStyle.MINIMAL, BgmMood.MINIMAL, null, null, 0xFF121212.toInt()),
    )

    fun of(id: ShortsTemplateId): ShortsTemplate = all.first { it.id == id }
}

/** Smart Transition Agent: normalises suggested transitions to the template's language (max 2 kinds). */
object SmartTransition {
    fun choose(template: ShortsTemplate, sceneIndex: Int, purpose: String, suggested: TransitionType?): TransitionType {
        if (sceneIndex == 0) return TransitionType.NONE
        val allowed = setOf(template.transition, template.accentTransition)
        if (suggested != null && suggested in allowed) return suggested
        val p = purpose.lowercase()
        return if (p.contains("cta") || p.contains("특징") || p.contains("feature")) template.accentTransition else template.transition
    }
}
