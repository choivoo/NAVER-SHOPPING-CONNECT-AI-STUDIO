package com.shoppingconnect.aistudio.visual

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import com.shoppingconnect.aistudio.domain.model.ExtractedPalette
import kotlin.math.pow

object ColorTools {
    fun luminance(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }

    /** WCAG contrast ratio (1..21). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Returns [preferred] if it meets [minRatio] against [bg], else the better of near-black / white. */
    fun readableOn(bg: Int, preferred: Int, minRatio: Double = 4.5): Int {
        if (contrast(bg, preferred) >= minRatio) return preferred
        val dark = 0xFF141418.toInt(); val light = Color.WHITE
        return if (contrast(bg, dark) >= contrast(bg, light)) dark else light
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        val u = 1 - t
        return Color.argb(
            (Color.alpha(a) * u + Color.alpha(b) * t).toInt(), (Color.red(a) * u + Color.red(b) * t).toInt(),
            (Color.green(a) * u + Color.green(b) * t).toInt(), (Color.blue(a) * u + Color.blue(b) * t).toInt(),
        )
    }

    fun extract(bitmap: Bitmap): ExtractedPalette {
        val p = Palette.from(bitmap).maximumColorCount(16).generate()
        val primary = p.getVibrantColor(p.getDominantColor(0xFF5B4CF0.toInt()))
        val secondary = p.getMutedColor(p.getDarkMutedColor(0xFF2A2A3A.toInt()))
        val accent = p.getLightVibrantColor(p.getVibrantColor(0xFF00C2A8.toInt()))
        val bg = blend(p.getLightMutedColor(Color.WHITE), Color.WHITE, 0.6f)
        return ExtractedPalette(primary, secondary, accent, bg, readableOn(bg, 0xFF141418.toInt()))
    }
}
