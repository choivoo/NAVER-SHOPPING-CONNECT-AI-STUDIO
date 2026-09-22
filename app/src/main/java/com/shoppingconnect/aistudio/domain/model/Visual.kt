package com.shoppingconnect.aistudio.domain.model

import com.shoppingconnect.aistudio.core.common.newId
import kotlinx.serialization.Serializable

enum class CardType(val label: String) {
    HERO("대표 카드"), FEATURE("특징 카드"), SPEC("정보 카드"), PROS("장점 카드"),
    CHECK("구매 전 확인"), TARGET("추천 대상"), CTA("구매 안내"), COMPARISON("비교 카드"), PRICE("가격 카드"),
}

enum class CardStylePreset(val label: String) { CLEAN("Clean"), SOFT("Soft"), PREMIUM("Premium"), TECH("Tech"), CUTE("Cute"), MINIMAL("Minimal"), DARK("Dark") }

enum class AspectRatio(val label: String, val w: Int, val h: Int) {
    SQUARE("1:1", 1, 1), PORTRAIT_4_5("4:5", 4, 5), WIDE_16_9("16:9", 16, 9), PHOTO_3_2("3:2", 3, 2),
    STORY_9_16("9:16", 9, 16), BLOG_WIDE("Blog Wide", 2, 1);

    fun heightFor(width: Int) = width * h / w
}

enum class TextAlign { START, CENTER }

@Serializable
data class CardStyle(
    val preset: CardStylePreset = CardStylePreset.CLEAN,
    val aspect: AspectRatio = AspectRatio.SQUARE,
    val fontScale: Float = 1f,
    val bold: Boolean = true,
    val align: TextAlign = TextAlign.START,
    val backgroundArgb: Int? = null,
    val textArgb: Int? = null,
    val accentArgb: Int? = null,
    val paddingDp: Int = 64,
    val cornerRadius: Int = 36,
    val shadow: Boolean = true,
    val overlayAlpha: Float = 0f,
    val imageZoom: Float = 1f,
    val imageOffsetX: Float = 0f,
    val imageOffsetY: Float = 0f,
    val brandText: String = "",
    val showImage: Boolean = true,
)

/**
 * A blog visual card. Cards are *app-generated graphics* (never presented as photos);
 * product photos inside them come only from the product source or the user.
 */
@Serializable
data class BlogCard(
    val id: String = newId(),
    val type: CardType,
    val title: String,
    val subtitle: String = "",
    val items: List<String> = emptyList(),
    val rows: List<Spec> = emptyList(),
    val cta: String = "",
    val imageAssetId: String? = null,
    val style: CardStyle = CardStyle(),
    val afterBlockId: String? = null,
    val renderedAssetId: String? = null,
)

@Serializable
data class VisualPlan(
    val visualTheme: String = "",
    val stylePreset: CardStylePreset = CardStylePreset.CLEAN,
    val cards: List<BlogCard> = emptyList(),
    val heroImageIndex: Int = 0,
    val shortsAssetIds: List<String> = emptyList(),
)

@Serializable
data class ExtractedPalette(val primary: Int, val secondary: Int, val accent: Int, val background: Int, val onBackground: Int)
