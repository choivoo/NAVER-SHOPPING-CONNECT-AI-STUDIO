package com.shoppingconnect.aistudio.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.shoppingconnect.aistudio.domain.model.BlogCard
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.CardType
import com.shoppingconnect.aistudio.domain.model.ExtractedPalette
import com.shoppingconnect.aistudio.domain.model.TextAlign

data class CardTheme(val bg: Int, val bg2: Int, val surface: Int, val text: Int, val subText: Int, val accent: Int, val onAccent: Int)

/**
 * Renders blog visual cards to bitmaps with the Android Canvas (hardware independent, exact
 * output at any export width). Auto-layout picks image placement from the image's aspect ratio,
 * and every text block is fitted into its safe area (shrinks, then ellipsizes) so nothing clips.
 */
object CardRenderer {

    fun theme(preset: CardStylePreset, palette: ExtractedPalette?, card: BlogCard): CardTheme {
        val base = when (preset) {
            CardStylePreset.CLEAN -> CardTheme(Color.WHITE, 0xFFF4F5F9.toInt(), 0xFFF4F5F9.toInt(), 0xFF15151C.toInt(), 0xFF5A5B66.toInt(), 0xFF5B4CF0.toInt(), Color.WHITE)
            CardStylePreset.SOFT -> CardTheme(0xFFFFF6F0.toInt(), 0xFFFDE8EF.toInt(), Color.WHITE, 0xFF3A2E35.toInt(), 0xFF7A6870.toInt(), 0xFFE57A9A.toInt(), Color.WHITE)
            CardStylePreset.PREMIUM -> CardTheme(0xFF14120F.toInt(), 0xFF2B241A.toInt(), 0xFF221D16.toInt(), 0xFFF7EEDB.toInt(), 0xFFC9BC9E.toInt(), 0xFFD4AF6A.toInt(), 0xFF14120F.toInt())
            CardStylePreset.TECH -> CardTheme(0xFF0B1220.toInt(), 0xFF12213D.toInt(), 0xFF15233F.toInt(), 0xFFEAF2FF.toInt(), 0xFF9FB3D1.toInt(), 0xFF3DDCFF.toInt(), 0xFF0B1220.toInt())
            CardStylePreset.CUTE -> CardTheme(0xFFFFF9E6.toInt(), 0xFFFFEFD1.toInt(), Color.WHITE, 0xFF3B3226.toInt(), 0xFF7D6E58.toInt(), 0xFFFF9F43.toInt(), Color.WHITE)
            CardStylePreset.MINIMAL -> CardTheme(0xFFFAFAFA.toInt(), 0xFFFAFAFA.toInt(), 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF666666.toInt(), 0xFF111111.toInt(), Color.WHITE)
            CardStylePreset.DARK -> CardTheme(0xFF121218.toInt(), 0xFF1D1D27.toInt(), 0xFF1F1F2A.toInt(), 0xFFF2F2F7.toInt(), 0xFFA9A9B8.toInt(), 0xFF8C7CFF.toInt(), Color.WHITE)
        }
        var t = base
        if (palette != null && preset in setOf(CardStylePreset.CLEAN, CardStylePreset.SOFT, CardStylePreset.CUTE)) {
            t = t.copy(accent = palette.primary, bg2 = ColorTools.blend(palette.background, t.bg, 0.3f))
        }
        val s = card.style
        s.backgroundArgb?.let { t = t.copy(bg = it, bg2 = it) }
        s.accentArgb?.let { t = t.copy(accent = it) }
        s.textArgb?.let { t = t.copy(text = it) }
        // Enforce readable contrast (WCAG AA) regardless of user/palette choices.
        return t.copy(
            text = ColorTools.readableOn(t.bg, t.text),
            subText = ColorTools.readableOn(t.bg, t.subText, 3.0),
            onAccent = ColorTools.readableOn(t.accent, t.onAccent),
        )
    }

    fun render(card: BlogCard, width: Int, image: Bitmap?, palette: ExtractedPalette?): Bitmap {
        val height = card.style.aspect.heightFor(width)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        draw(c, card, width, height, image, palette)
        return bmp
    }

    fun draw(c: Canvas, card: BlogCard, w: Int, h: Int, image: Bitmap?, palette: ExtractedPalette?) {
        val u = w / 1080f
        val s = card.style
        val t = theme(s.preset, palette, card)
        val pad = s.paddingDp * u
        val img = image?.takeIf { s.showImage }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), t.bg, t.bg2, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        when (card.type) {
            CardType.HERO -> drawHero(c, card, t, w, h, u, pad, img)
            CardType.CTA -> drawCta(c, card, t, w, h, u, pad, img)
            CardType.SPEC -> drawSpec(c, card, t, w, h, u, pad, img)
            else -> drawList(c, card, t, w, h, u, pad, img)
        }
        if (s.brandText.isNotBlank()) {
            val p = textPaint(22f * u, t.subText, false)
            val tw = p.measureText(s.brandText)
            c.drawText(s.brandText, w - pad - tw, h - pad * 0.45f, p)
        }
    }

    // ---------------------------------------------------------------------------------------

    private fun textPaint(size: Float, color: Int, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) 800 else 400, false)
    }

    /** Fits text in (maxW × maxH): shrinks from [start] to [min], then ellipsizes at [maxLines]. */
    fun fit(text: String, maxW: Int, maxH: Float, start: Float, min: Float, maxLines: Int, color: Int, bold: Boolean, align: TextAlign): StaticLayout {
        var size = start
        val alignment = if (align == TextAlign.CENTER) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
        while (true) {
            val p = textPaint(size, color, bold)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, maxW.coerceAtLeast(1))
                .setAlignment(alignment).setLineSpacing(0f, 1.18f).setIncludePad(false)
                .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
            val overflow = layout.lineCount >= maxLines && layout.getEllipsisCount(layout.lineCount - 1) > 0
            if ((layout.height <= maxH && !overflow) || size <= min) return layout
            size -= 2f
        }
    }

    private fun drawLayout(c: Canvas, l: StaticLayout, x: Float, y: Float) { c.save(); c.translate(x, y); l.draw(c); c.restore() }

    private fun drawImage(c: Canvas, img: Bitmap, dst: RectF, radius: Float, card: BlogCard, shadow: Boolean) {
        val s = card.style
        if (shadow) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.TRANSPARENT; setShadowLayer(radius * 0.6f, 0f, radius * 0.25f, 0x33000000) }
            sp.color = Color.WHITE
            c.drawRoundRect(dst, radius, radius, sp)
        }
        c.save()
        val path = Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) }
        c.clipPath(path)
        // center-crop, then user zoom/offset
        val scale = maxOf(dst.width() / img.width, dst.height() / img.height) * s.imageZoom.coerceIn(1f, 4f)
        val dw = img.width * scale; val dh = img.height * scale
        val maxDx = (dw - dst.width()) / 2; val maxDy = (dh - dst.height()) / 2
        val left = dst.centerX() - dw / 2 + s.imageOffsetX.coerceIn(-1f, 1f) * maxDx
        val top = dst.centerY() - dh / 2 + s.imageOffsetY.coerceIn(-1f, 1f) * maxDy
        c.drawBitmap(img, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        if (s.overlayAlpha > 0f) c.drawColor(Color.argb((s.overlayAlpha.coerceIn(0f, 0.9f) * 255).toInt(), 0, 0, 0))
        c.restore()
    }

    private fun drawHero(c: Canvas, card: BlogCard, t: CardTheme, w: Int, h: Int, u: Float, pad: Float, img: Bitmap?) {
        val s = card.style
        val radius = s.cornerRadius * u
        val portraitImg = img != null && img.height > img.width * 1.1f
        val wideCanvas = w > h * 1.2f
        val textW: Int
        val textX: Float
        var textTop: Float
        val textBottom = h - pad
        if (img != null && (wideCanvas || (portraitImg && w >= h))) {
            // left text / right image
            val imgRect = RectF(w * 0.52f, pad, w - pad, h - pad)
            drawImage(c, img, imgRect, radius, card, s.shadow)
            textX = pad; textW = (w * 0.52f - pad * 1.6f).toInt(); textTop = pad
        } else if (img != null) {
            // top image / bottom text
            val imgH = h * if (h > w * 1.3f) 0.58f else 0.54f
            drawImage(c, img, RectF(pad, pad, w - pad, pad + imgH), radius, card, s.shadow)
            textX = pad; textW = (w - pad * 2).toInt(); textTop = pad + imgH + pad * 0.6f
        } else {
            textX = pad; textW = (w - pad * 2).toInt(); textTop = h * 0.28f
        }
        val available = textBottom - textTop
        val title = fit(card.title, textW, available * 0.6f, 84f * u * s.fontScale, 36f * u, 3, t.text, s.bold, s.align)
        drawLayout(c, title, textX, textTop)
        textTop += title.height + 18f * u
        if (card.subtitle.isNotBlank()) {
            val sub = fit(card.subtitle, textW, (textBottom - textTop) * 0.7f, 40f * u * s.fontScale, 24f * u, 3, t.subText, false, s.align)
            drawLayout(c, sub, textX, textTop)
            textTop += sub.height + 24f * u
        }
        if (card.cta.isNotBlank() && textBottom - textTop > 90 * u) pill(c, card.cta, t, textX, textTop, u, textW, s.align)
    }

    private fun pill(c: Canvas, text: String, t: CardTheme, x: Float, y: Float, u: Float, maxW: Int, align: TextAlign) {
        val p = textPaint(34f * u, t.onAccent, true)
        val tw = p.measureText(text).coerceAtMost(maxW - 80 * u)
        val pw = tw + 80 * u; val ph = 84 * u
        val left = if (align == TextAlign.CENTER) x + (maxW - pw) / 2 else x
        c.drawRoundRect(RectF(left, y, left + pw, y + ph), ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent })
        val label = TextUtils.ellipsize(text, p, tw, TextUtils.TruncateAt.END).toString()
        c.drawText(label, left + 40 * u, y + ph / 2 - (p.descent() + p.ascent()) / 2, p)
    }

    private fun drawList(c: Canvas, card: BlogCard, t: CardTheme, w: Int, h: Int, u: Float, pad: Float, img: Bitmap?) {
        val s = card.style
        var y = pad
        val contentW = (w - pad * 2).toInt()
        val label = when (card.type) {
            CardType.FEATURE -> "FEATURES"; CardType.PROS -> "GOOD POINTS"; CardType.CHECK -> "CHECK BEFORE BUY"
            CardType.TARGET -> "RECOMMENDED FOR"; CardType.COMPARISON -> "COMPARE"; CardType.PRICE -> "PRICE"; else -> ""
        }
        if (label.isNotEmpty()) {
            val lp = textPaint(24f * u, t.accent, true).apply { letterSpacing = 0.12f }
            c.drawText(label, pad, y + 24 * u, lp); y += 52 * u
        }
        val imgBox = if (img != null && h > w * 0.9f) (h * 0.26f) else 0f
        val titleLayout = fit(card.title, contentW, h * 0.2f, 64f * u * s.fontScale, 32f * u, 2, t.text, s.bold, s.align)
        drawLayout(c, titleLayout, pad, y); y += titleLayout.height + 36 * u
        val bottomLimit = h - pad - imgBox - (if (imgBox > 0) pad * 0.5f else 0f)
        val items = card.items.ifEmpty { listOfNotNull(card.subtitle.takeIf { it.isNotBlank() }) }.take(5)
        if (items.isNotEmpty()) {
            val per = (bottomLimit - y) / items.size
            items.forEachIndexed { i, item ->
                val rowTop = y + per * i
                val rowRect = RectF(pad, rowTop, w - pad, rowTop + per - 16 * u)
                val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = t.surface
                    if (s.shadow) setShadowLayer(12 * u, 0f, 4 * u, 0x1A000000)
                }
                c.drawRoundRect(rowRect, s.cornerRadius * u * 0.6f, s.cornerRadius * u * 0.6f, surface)
                val badge = 56 * u
                val bx = rowRect.left + 24 * u; val by = rowRect.centerY() - badge / 2
                c.drawOval(RectF(bx, by, bx + badge, by + badge), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent })
                val np = textPaint(28f * u, t.onAccent, true)
                val mark = if (card.type == CardType.CHECK) "✓" else "${i + 1}"
                c.drawText(mark, bx + badge / 2 - np.measureText(mark) / 2, by + badge / 2 - (np.descent() + np.ascent()) / 2, np)
                val tx = bx + badge + 24 * u
                val tl = fit(item, (rowRect.right - tx - 24 * u).toInt(), rowRect.height() - 20 * u, 38f * u * s.fontScale, 22f * u, 3, ColorTools.readableOn(t.surface, t.text), false, TextAlign.START)
                drawLayout(c, tl, tx, rowRect.centerY() - tl.height / 2f)
            }
        }
        if (img != null && imgBox > 0) drawImage(c, img, RectF(pad, h - pad - imgBox, w - pad, h - pad), s.cornerRadius * u, card, s.shadow)
    }

    private fun drawSpec(c: Canvas, card: BlogCard, t: CardTheme, w: Int, h: Int, u: Float, pad: Float, img: Bitmap?) {
        val s = card.style
        var y = pad
        val contentW = (w - pad * 2).toInt()
        val titleLayout = fit(card.title, contentW, h * 0.14f, 60f * u * s.fontScale, 30f * u, 2, t.text, s.bold, s.align)
        drawLayout(c, titleLayout, pad, y); y += titleLayout.height + 32 * u
        val rows = card.rows.take(8)
        if (rows.isEmpty()) {
            val l = fit("확인된 제품 정보가 없습니다.\n판매 페이지에서 확인해 주세요.", contentW, h * 0.3f, 36f * u, 22f * u, 3, t.subText, false, TextAlign.START)
            drawLayout(c, l, pad, y); return
        }
        val imgBox = if (img != null && h >= w) h * 0.22f else 0f
        val rowH = ((h - pad - y - imgBox) / rows.size).coerceAtMost(120 * u)
        val keyW = contentW * 0.34f
        rows.forEachIndexed { i, r ->
            val top = y + rowH * i
            if (i % 2 == 0) c.drawRoundRect(RectF(pad, top, w - pad, top + rowH), 16 * u, 16 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.surface })
            val k = fit(r.name, (keyW - 24 * u).toInt(), rowH - 12 * u, 30f * u, 18f * u, 2, t.subText, true, TextAlign.START)
            val v = fit(r.value, (contentW - keyW - 24 * u).toInt(), rowH - 12 * u, 32f * u, 18f * u, 2, ColorTools.readableOn(if (i % 2 == 0) t.surface else t.bg, t.text), false, TextAlign.START)
            drawLayout(c, k, pad + 20 * u, top + (rowH - k.height) / 2)
            drawLayout(c, v, pad + keyW, top + (rowH - v.height) / 2)
        }
        if (img != null && imgBox > 0) drawImage(c, img, RectF(pad, h - pad - imgBox + 16 * u, w - pad, h - pad), s.cornerRadius * u, card, s.shadow)
    }

    private fun drawCta(c: Canvas, card: BlogCard, t: CardTheme, w: Int, h: Int, u: Float, pad: Float, img: Bitmap?) {
        val s = card.style
        var textColor = t.text
        if (img != null) {
            drawImage(c, img, RectF(0f, 0f, w.toFloat(), h.toFloat()), 0f, card.copy(style = s.copy(overlayAlpha = maxOf(s.overlayAlpha, 0.55f))), false)
            textColor = Color.WHITE
        }
        val contentW = (w - pad * 2).toInt()
        val title = fit(card.title, contentW, h * 0.4f, 76f * u * s.fontScale, 34f * u, 3, textColor, s.bold, TextAlign.CENTER)
        var y = h * 0.36f - title.height / 2f
        drawLayout(c, title, pad, y)
        y += title.height + 28 * u
        if (card.subtitle.isNotBlank()) {
            val sub = fit(card.subtitle, contentW, h * 0.2f, 36f * u, 22f * u, 2, if (img != null) 0xFFE8E8EE.toInt() else t.subText, false, TextAlign.CENTER)
            drawLayout(c, sub, pad, y); y += sub.height + 36 * u
        }
        pill(c, card.cta.ifBlank { "상품 보러가기" }, t, pad, y + 20 * u, u, contentW, TextAlign.CENTER)
    }
}
