package com.shoppingconnect.aistudio.media.video

import android.graphics.Color
import com.shoppingconnect.aistudio.domain.model.SubtitleStyle
import com.shoppingconnect.aistudio.domain.model.TextStylePreset

data class TextLook(
    val sizeMul: Float,
    val weight: Int,
    val color: Int,
    val boxColor: Int?,
    val strokeColor: Int?,
    val strokeWidth: Float,
    val shadow: Boolean,
    val highlightColor: Int,
    val padding: Float,
)

object TextStyles {
    fun of(p: TextStylePreset): TextLook = when (p) {
        TextStylePreset.BOLD_COMMERCE -> TextLook(1.0f, 900, Color.WHITE, 0xE6FF3D57.toInt(), null, 0f, true, 0xFFFFE14D.toInt(), 28f)
        TextStylePreset.MINIMAL_CAPTION -> TextLook(0.8f, 600, Color.WHITE, null, null, 0f, true, 0xFF9EE7FF.toInt(), 0f)
        TextStylePreset.NEWS -> TextLook(0.85f, 800, Color.WHITE, 0xF0142850.toInt(), null, 0f, false, 0xFFFFD166.toInt(), 22f)
        TextStylePreset.PRODUCT_REVIEW -> TextLook(0.95f, 800, 0xFF111111.toInt(), 0xF2FFFFFF.toInt(), null, 0f, true, 0xFFFF5A5F.toInt(), 24f)
        TextStylePreset.PREMIUM -> TextLook(0.9f, 500, 0xFFF7EEDB.toInt(), 0xB3000000.toInt(), null, 0f, false, 0xFFD4AF6A.toInt(), 26f)
        TextStylePreset.CUTE -> TextLook(1.0f, 900, 0xFFFF7AA2.toInt(), 0xF2FFF6E0.toInt(), 0xFFFFFFFF.toInt(), 6f, true, 0xFFFF9F43.toInt(), 26f)
        TextStylePreset.TECH -> TextLook(0.9f, 800, 0xFF3DDCFF.toInt(), 0xE60B1220.toInt(), null, 0f, false, 0xFFB4FF39.toInt(), 22f)
    }

    fun subtitle(s: SubtitleStyle): TextLook = when (s) {
        SubtitleStyle.CLASSIC -> TextLook(1f, 700, Color.WHITE, 0x99000000.toInt(), null, 0f, false, 0xFFFFE14D.toInt(), 16f)
        SubtitleStyle.BOLD -> TextLook(1.15f, 900, Color.WHITE, null, 0xFF000000.toInt(), 10f, true, 0xFFFFE14D.toInt(), 0f)
        SubtitleStyle.MINIMAL -> TextLook(0.9f, 500, Color.WHITE, null, null, 0f, true, 0xFFB5F5FF.toInt(), 0f)
        SubtitleStyle.KOREAN_SHORTS -> TextLook(1.1f, 900, Color.WHITE, null, 0xFF111111.toInt(), 12f, true, 0xFFFFD400.toInt(), 0f)
        SubtitleStyle.HIGHLIGHT -> TextLook(1.05f, 800, 0xFF111111.toInt(), 0xF2FFE14D.toInt(), null, 0f, false, 0xFFE8173B.toInt(), 18f)
    }
}

/** 9:16 platform safe zones (fractions of the frame) — top UI, bottom caption/controls, side buttons. */
object SafeZone {
    const val TOP = 0.11f
    const val BOTTOM = 0.20f
    const val SIDE = 0.06f
    const val RIGHT_ACTIONS = 0.14f

    fun isInside(xFrac: Float, yFrac: Float): Boolean =
        yFrac >= TOP && yFrac <= 1 - BOTTOM && xFrac >= SIDE && xFrac <= 1 - RIGHT_ACTIONS
}
