package com.shoppingconnect.aistudio.media.shorts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import com.shoppingconnect.aistudio.domain.model.TextAlign
import com.shoppingconnect.aistudio.domain.model.ThumbnailSpec
import com.shoppingconnect.aistudio.media.video.SafeZone
import com.shoppingconnect.aistudio.visual.CardRenderer

/** 9:16 thumbnail: product image + big title + short hook (+ badge/sticker), text kept inside the safe zone. */
object ThumbnailRenderer {
    const val LAYOUTS = 3

    fun render(spec: ThumbnailSpec, image: Bitmap?, width: Int = 1080): Bitmap {
        val height = width * 16 / 9
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val u = width / 1080f
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), spec.gradientTopArgb, spec.gradientBottomArgb, Shader.TileMode.CLAMP)
        })
        val pad = width * SafeZone.SIDE + 12 * u
        val safeTop = height * SafeZone.TOP
        val safeBottom = height * (1 - SafeZone.BOTTOM)
        val contentW = (width * (1 - SafeZone.SIDE - SafeZone.RIGHT_ACTIONS) - pad).toInt()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        when (spec.layout % LAYOUTS) {
            0 -> { // full-bleed image, text bottom
                image?.let { c.drawBitmap(it, null, cover(it, width, height), paint) }
                c.drawRect(0f, height * 0.45f, width.toFloat(), height.toFloat(), Paint().apply {
                    shader = LinearGradient(0f, height * 0.45f, 0f, height.toFloat(), 0x00000000, 0xE6000000.toInt(), Shader.TileMode.CLAMP)
                })
                drawTexts(c, spec, pad, safeBottom - 520 * u, contentW, u, 0xFFFFFFFF.toInt(), 0xFFFFE14D.toInt())
            }
            1 -> { // title top, image framed center
                drawTexts(c, spec, pad, safeTop + 20 * u, contentW, u, 0xFFFFFFFF.toInt(), 0xFFFFE14D.toInt())
                image?.let {
                    val box = RectF(pad, height * 0.42f, width - pad, safeBottom)
                    c.save(); c.clipRect(box); c.drawBitmap(it, null, coverIn(it, box), paint); c.restore()
                }
            }
            else -> { // image top half, bold band
                image?.let { val box = RectF(0f, 0f, width.toFloat(), height * 0.58f); c.save(); c.clipRect(box); c.drawBitmap(it, null, coverIn(it, box), paint); c.restore() }
                c.drawRect(0f, height * 0.56f, width.toFloat(), height.toFloat(), Paint().apply { color = spec.gradientTopArgb })
                drawTexts(c, spec, pad, height * 0.6f, contentW, u, 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), boxedHook = true)
            }
        }
        if (spec.badge.isNotBlank()) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40 * u; color = 0xFF111111.toInt(); isFakeBoldText = true }
            val w = p.measureText(spec.badge) + 48 * u
            val r = RectF(pad, safeTop, pad + w, safeTop + 76 * u)
            c.drawRoundRect(r, 38 * u, 38 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE14D.toInt() })
            c.drawText(spec.badge, r.left + 24 * u, r.centerY() - (p.descent() + p.ascent()) / 2, p)
        }
        if (spec.sticker.isNotBlank()) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 150 * u }
            c.drawText(spec.sticker, width * (1 - SafeZone.RIGHT_ACTIONS) - 170 * u, safeTop + 180 * u, p)
        }
        return bmp
    }

    private fun drawTexts(c: Canvas, spec: ThumbnailSpec, x: Float, top: Float, w: Int, u: Float, titleColor: Int, hookColor: Int, boxedHook: Boolean = false) {
        val title = CardRenderer.fit(spec.title.ifBlank { " " }, w, 420 * u, 120 * u, 56 * u, 3, titleColor, true, TextAlign.START)
        c.save(); c.translate(x, top); title.draw(c); c.restore()
        if (spec.hook.isNotBlank()) {
            val hook = CardRenderer.fit(spec.hook, w, 160 * u, 54 * u, 30 * u, 2, hookColor, true, TextAlign.START)
            val y = top + title.height + 28 * u
            if (boxedHook) c.drawRoundRect(RectF(x - 16 * u, y - 12 * u, x + hook.width + 16 * u, y + hook.height + 12 * u), 16 * u, 16 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE14D.toInt() })
            c.save(); c.translate(x, y); hook.draw(c); c.restore()
        }
    }

    private fun cover(b: Bitmap, w: Int, h: Int) = coverIn(b, RectF(0f, 0f, w.toFloat(), h.toFloat()))

    private fun coverIn(b: Bitmap, box: RectF): RectF {
        val s = maxOf(box.width() / b.width, box.height() / b.height)
        val bw = b.width * s; val bh = b.height * s
        return RectF(box.centerX() - bw / 2, box.centerY() - bh / 2, box.centerX() + bw / 2, box.centerY() + bh / 2)
    }
}
