package com.shoppingconnect.aistudio.media.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import com.shoppingconnect.aistudio.domain.model.FitMode
import com.shoppingconnect.aistudio.domain.model.MotionEffect
import com.shoppingconnect.aistudio.domain.model.Scene
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.domain.model.SubtitleCue
import com.shoppingconnect.aistudio.domain.model.TextAnimation
import com.shoppingconnect.aistudio.domain.model.TextClip
import com.shoppingconnect.aistudio.domain.model.TransitionType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Supplies decoded media for scenes (images, cards, video frames). */
interface FrameMediaSource {
    fun image(scene: Scene): Bitmap?
    fun blurred(scene: Scene): Bitmap?
    fun videoFrame(scene: Scene, localMs: Long): Bitmap?
}

/**
 * Draws one frame of the timeline at time t. The same code renders the live editor preview
 * and the exported MP4, so what you see is what you export.
 */
class FrameRenderer(
    private val timeline: ShortTimeline,
    private val width: Int,
    private val height: Int,
    private val media: FrameMediaSource,
) {
    private val u = width / 1080f
    private val starts = timeline.sceneStarts()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val layoutCache = HashMap<String, StaticLayout>()

    fun draw(c: Canvas, tMs: Long, preview: Boolean = false) {
        c.drawColor(Color.BLACK)
        if (timeline.scenes.isEmpty()) return
        val idx = timeline.sceneAt(tMs)
        val scene = timeline.scenes[idx]
        val local = tMs - starts[idx]
        val tr = scene.transitionIn
        val trMs = if (idx > 0 && tr.type != TransitionType.NONE) tr.clampedMs.coerceAtMost(minOf(scene.durationMs, timeline.scenes[idx - 1].durationMs) / 2) else 0
        if (idx > 0 && local < trMs) {
            val prev = timeline.scenes[idx - 1]
            val prevLocal = tMs - starts[idx - 1]
            drawTransition(c, tr.type, local.toFloat() / trMs, prev, prevLocal, scene, local)
        } else {
            drawScene(c, scene, local, 1f, 0f, 0f, 1f)
        }
        timeline.texts.filter { tMs in it.startMs until it.endMs }.forEach { drawTextClip(c, it, tMs) }
        if (timeline.subtitlesEnabled) timeline.subtitles.firstOrNull { tMs in it.startMs until it.endMs }?.let { drawSubtitle(c, it) }
        timeline.stickers.filter { tMs in it.startMs until it.endMs }.forEach { s ->
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = s.sizePx * u }
            val appear = ((tMs - s.startMs) / 250f).coerceIn(0f, 1f)
            c.save(); c.scale(0.6f + 0.4f * easeOutBack(appear), 0.6f + 0.4f * easeOutBack(appear), s.xFraction * width, s.yFraction * height)
            c.drawText(s.emoji, s.xFraction * width - p.measureText(s.emoji) / 2, s.yFraction * height, p)
            c.restore()
        }
        timeline.watermark?.takeIf { it.isNotBlank() }?.let { wm ->
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30 * u; color = 0x99FFFFFF.toInt(); typeface = Typeface.create(Typeface.DEFAULT, 700, false) }
            c.drawText(wm, width * (1 - SafeZone.RIGHT_ACTIONS) - p.measureText(wm), height * (1 - SafeZone.BOTTOM) - 16 * u, p)
        }
        if (preview && timeline.showSafeZone) drawSafeZone(c)
    }

    // ---- scenes & motion -------------------------------------------------------------------

    private fun drawScene(c: Canvas, s: Scene, localMs: Long, alpha: Float, dx: Float, dy: Float, extraScale: Float) {
        val p = (localMs.toFloat() / s.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        val bmp = if (s.isVideo) media.videoFrame(s, s.trimStartMs + (localMs * s.speed).toLong()) else media.image(s)
        c.save()
        c.translate(dx, dy)
        bmpPaint.alpha = (alpha * 255).toInt()
        if (bmp == null) {
            val g = Paint().apply {
                shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), s.backgroundArgb, blendDark(s.backgroundArgb), Shader.TileMode.CLAMP)
                this.alpha = (alpha * 255).toInt()
            }
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), g)
            c.restore(); return
        }
        var scale = 1f; var mx = 0f; var my = 0f
        when (s.motion) {
            MotionEffect.NONE -> Unit
            MotionEffect.KEN_BURNS_IN -> scale = 1f + 0.14f * easeInOut(p)
            MotionEffect.KEN_BURNS_OUT -> scale = 1.14f - 0.14f * easeInOut(p)
            MotionEffect.PAN_LEFT -> { scale = 1.12f; mx = (0.05f - 0.1f * easeInOut(p)) * width }
            MotionEffect.PAN_RIGHT -> { scale = 1.12f; mx = (-0.05f + 0.1f * easeInOut(p)) * width }
            MotionEffect.PARALLAX -> { scale = 1.1f; my = (0.03f - 0.06f * p) * height; mx = 0.02f * width * sin(p * PI).toFloat() }
            MotionEffect.PULSE -> scale = 1f + 0.035f * sin(2 * PI * p * 2).toFloat().let { abs(it) }
        }
        if (s.fit == FitMode.FIT) {
            media.blurred(s)?.let { bg -> c.drawBitmap(bg, null, coverRect(bg, 1.05f, 0f, 0f), bmpPaint) }
            c.drawColor(Color.argb((0.25f * alpha * 255).toInt(), 0, 0, 0))
        }
        c.save()
        c.rotate(s.rotation.toFloat(), width / 2f, height / 2f)
        val zoom = s.zoom.coerceIn(0.5f, 4f) * scale * extraScale
        val dst = if (s.fit == FitMode.FILL) coverRect(bmp, zoom, s.offsetX, s.offsetY) else fitRect(bmp, zoom, s.offsetX, s.offsetY)
        dst.offset(mx, my)
        c.drawBitmap(bmp, null, dst, bmpPaint)
        c.restore()
        c.restore()
    }

    private fun blendDark(c: Int) = Color.rgb((Color.red(c) * 0.35f).toInt(), (Color.green(c) * 0.35f).toInt(), (Color.blue(c) * 0.35f).toInt())

    private fun coverRect(b: Bitmap, zoom: Float, ox: Float, oy: Float): RectF {
        val s = maxOf(width.toFloat() / b.width, height.toFloat() / b.height) * zoom
        val w = b.width * s; val h = b.height * s
        val maxDx = (w - width) / 2; val maxDy = (h - height) / 2
        val l = (width - w) / 2 + ox.coerceIn(-1f, 1f) * maxOf(maxDx, 0f)
        val t = (height - h) / 2 + oy.coerceIn(-1f, 1f) * maxOf(maxDy, 0f)
        return RectF(l, t, l + w, t + h)
    }

    private fun fitRect(b: Bitmap, zoom: Float, ox: Float, oy: Float): RectF {
        val s = minOf(width.toFloat() / b.width, height.toFloat() / b.height) * zoom
        val w = b.width * s; val h = b.height * s
        val l = (width - w) / 2 + ox * width * 0.25f
        val t = (height - h) / 2 + oy * height * 0.25f
        return RectF(l, t, l + w, t + h)
    }

    private fun drawTransition(c: Canvas, type: TransitionType, q: Float, prev: Scene, prevLocal: Long, cur: Scene, curLocal: Long) {
        val e = easeInOut(q)
        val w = width.toFloat(); val h = height.toFloat()
        when (type) {
            TransitionType.FADE -> if (q < 0.5f) {
                drawScene(c, prev, prevLocal, 1f, 0f, 0f, 1f); c.drawColor(Color.argb((q * 2 * 255).toInt(), 0, 0, 0))
            } else {
                drawScene(c, cur, curLocal, 1f, 0f, 0f, 1f); c.drawColor(Color.argb(((1 - q) * 2 * 255).toInt().coerceIn(0, 255), 0, 0, 0))
            }
            TransitionType.CROSS_FADE, TransitionType.NONE -> { drawScene(c, prev, prevLocal, 1f, 0f, 0f, 1f); drawScene(c, cur, curLocal, e, 0f, 0f, 1f) }
            TransitionType.SLIDE_LEFT -> { drawScene(c, prev, prevLocal, 1f, -e * w, 0f, 1f); drawScene(c, cur, curLocal, 1f, (1 - e) * w, 0f, 1f) }
            TransitionType.SLIDE_RIGHT -> { drawScene(c, prev, prevLocal, 1f, e * w, 0f, 1f); drawScene(c, cur, curLocal, 1f, -(1 - e) * w, 0f, 1f) }
            TransitionType.SLIDE_UP -> { drawScene(c, prev, prevLocal, 1f, 0f, -e * h, 1f); drawScene(c, cur, curLocal, 1f, 0f, (1 - e) * h, 1f) }
            TransitionType.SLIDE_DOWN -> { drawScene(c, prev, prevLocal, 1f, 0f, e * h, 1f); drawScene(c, cur, curLocal, 1f, 0f, -(1 - e) * h, 1f) }
            TransitionType.ZOOM_IN -> { drawScene(c, prev, prevLocal, 1f - e, 0f, 0f, 1f + 0.3f * e); drawScene(c, cur, curLocal, e, 0f, 0f, 1.3f - 0.3f * e) }
            TransitionType.ZOOM_OUT -> { drawScene(c, prev, prevLocal, 1f, 0f, 0f, 1f - 0.2f * e); drawScene(c, cur, curLocal, e, 0f, 0f, 0.8f + 0.2f * e) }
            TransitionType.BLUR -> {
                val pb = media.blurred(prev); val cb = media.blurred(cur)
                if (q < 0.5f) {
                    drawScene(c, prev, prevLocal, 1f, 0f, 0f, 1f)
                    pb?.let { bmpPaint.alpha = (q * 2 * 255).toInt(); c.drawBitmap(it, null, coverRect(it, 1f, 0f, 0f), bmpPaint) }
                } else {
                    drawScene(c, cur, curLocal, 1f, 0f, 0f, 1f)
                    cb?.let { bmpPaint.alpha = ((1 - q) * 2 * 255).toInt().coerceIn(0, 255); c.drawBitmap(it, null, coverRect(it, 1f, 0f, 0f), bmpPaint) }
                }
            }
            TransitionType.FLASH -> {
                if (q < 0.5f) drawScene(c, prev, prevLocal, 1f, 0f, 0f, 1f) else drawScene(c, cur, curLocal, 1f, 0f, 0f, 1f)
                val a = (1 - abs(q - 0.5f) * 2).coerceIn(0f, 1f)
                c.drawColor(Color.argb((a * 235).toInt(), 255, 255, 255))
            }
            TransitionType.WIPE -> {
                drawScene(c, prev, prevLocal, 1f, 0f, 0f, 1f)
                c.save(); c.clipRect(0f, 0f, w * e, h); drawScene(c, cur, curLocal, 1f, 0f, 0f, 1f); c.restore()
            }
            TransitionType.SPIN_LIGHT -> {
                drawScene(c, prev, prevLocal, 1f - e, 0f, 0f, 1f)
                c.save(); c.rotate(-8f * (1 - e), w / 2, h / 2); drawScene(c, cur, curLocal, e, 0f, 0f, 1.1f - 0.1f * e); c.restore()
            }
        }
    }

    // ---- text ------------------------------------------------------------------------------

    private fun paintFor(look: TextLook, size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; color = look.color
        typeface = Typeface.create(Typeface.DEFAULT, look.weight, false)
        if (look.shadow) setShadowLayer(8 * u, 0f, 3 * u, 0x99000000.toInt())
    }

    private fun layout(key: String, text: CharSequence, p: TextPaint, maxW: Int): StaticLayout = layoutCache.getOrPut(key) {
        StaticLayout.Builder.obtain(text, 0, text.length, p, maxW).setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f).setIncludePad(false).setMaxLines(4).build()
    }

    private fun spanned(text: String, highlights: List<String>, color: Int): CharSequence {
        if (highlights.isEmpty()) return text
        val sp = SpannableString(text)
        highlights.forEach { h ->
            var i = text.indexOf(h)
            while (i >= 0) { sp.setSpan(ForegroundColorSpan(color), i, i + h.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i = text.indexOf(h, i + h.length) }
        }
        return sp
    }

    private fun drawTextClip(c: Canvas, clip: TextClip, tMs: Long) {
        val look = TextStyles.of(clip.style)
        val local = tMs - clip.startMs
        val remaining = clip.endMs - tMs
        val enterP = (local / 380f).coerceIn(0f, 1f)
        val exitP = if (clip.exit == TextAnimation.NONE) 1f else (remaining / 260f).coerceIn(0f, 1f)
        var text = clip.text
        if (clip.enter == TextAnimation.TYPEWRITER) {
            val n = (text.length * (local / 700f)).toInt().coerceIn(0, text.length)
            text = text.take(n)
            if (text.isEmpty()) return
        }
        val size = clip.sizeSp * u * look.sizeMul
        val p = paintFor(look, size)
        val maxW = (width * (1 - SafeZone.SIDE * 2 - 0.08f)).toInt()
        val l = layout("t:${clip.id}:${text.length}:$size", spanned(text, listOfNotNull(clip.highlight), look.highlightColor), p, maxW)
        val cx = width / 2f
        val y = (clip.yFraction.coerceIn(SafeZone.TOP, 1 - SafeZone.BOTTOM - 0.05f)) * height
        var alpha = 1f; var scale = 1f; var ty = 0f; var clipW = 1f
        when (clip.enter) {
            TextAnimation.FADE -> alpha = enterP
            TextAnimation.SLIDE -> { alpha = enterP; ty = (1 - easeOut(enterP)) * 80 * u }
            TextAnimation.POP -> { scale = 0.6f + 0.4f * easeOutBack(enterP); alpha = (enterP * 2).coerceAtMost(1f) }
            TextAnimation.SCALE -> { scale = 1.4f - 0.4f * easeOut(enterP); alpha = enterP }
            TextAnimation.BOUNCE -> ty = -abs(sin(enterP * PI * 2).toFloat()) * 40 * u * (1 - enterP)
            TextAnimation.MASK_REVEAL -> clipW = easeOut(enterP)
            TextAnimation.TYPEWRITER, TextAnimation.NONE -> Unit
        }
        when (clip.exit) {
            TextAnimation.FADE, TextAnimation.MASK_REVEAL, TextAnimation.TYPEWRITER -> alpha *= exitP
            TextAnimation.SLIDE -> { alpha *= exitP; ty -= (1 - exitP) * 60 * u }
            TextAnimation.POP, TextAnimation.SCALE -> { scale *= 0.7f + 0.3f * exitP; alpha *= exitP }
            TextAnimation.BOUNCE -> alpha *= exitP
            TextAnimation.NONE -> Unit
        }
        drawTextBlock(c, l, look, cx, y + ty, alpha, scale, clipW)
    }

    private fun drawTextBlock(c: Canvas, l: StaticLayout, look: TextLook, cx: Float, top: Float, alpha: Float, scale: Float, clipW: Float) {
        var lw = 0f
        for (i in 0 until l.lineCount) lw = maxOf(lw, l.getLineWidth(i))
        val pad = look.padding * u
        val left = cx - l.width / 2f
        c.save()
        c.scale(scale, scale, cx, top + l.height / 2f)
        if (clipW < 1f) c.clipRect(cx - lw / 2 - pad, top - pad, cx - lw / 2 - pad + (lw + pad * 2) * clipW, top + l.height + pad)
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        look.boxColor?.let { bc ->
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bc; this.alpha = (Color.alpha(bc) * alpha).toInt() }
            c.drawRoundRect(RectF(cx - lw / 2 - pad, top - pad * 0.7f, cx + lw / 2 + pad, top + l.height + pad * 0.7f), 18 * u, 18 * u, bp)
        }
        c.translate(left, top)
        if (look.strokeColor != null && look.strokeWidth > 0) {
            val stroke = TextPaint(l.paint).apply { style = Paint.Style.STROKE; strokeWidth = look.strokeWidth * u; color = look.strokeColor; strokeJoin = Paint.Join.ROUND; this.alpha = a; clearShadowLayer() }
            StaticLayout.Builder.obtain(l.text, 0, l.text.length, stroke, l.width).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.1f).setIncludePad(false).setMaxLines(4).build().draw(c)
        }
        l.paint.alpha = a
        l.draw(c)
        c.restore()
    }

    private fun drawSubtitle(c: Canvas, cue: SubtitleCue) {
        val look = TextStyles.subtitle(timeline.subtitleStyle)
        val size = 54 * u * look.sizeMul
        val p = paintFor(look, size)
        val maxW = (width * 0.78f).toInt()
        val l = layout("s:${cue.id}:${timeline.subtitleStyle}", spanned(cue.text, cue.highlights, look.highlightColor), p, maxW)
        val top = height * (1 - SafeZone.BOTTOM) - l.height - 40 * u
        drawTextBlock(c, l, look, width / 2f, top, 1f, 1f, 1f)
    }

    private fun drawSafeZone(c: Canvas) {
        val shade = Paint().apply { color = 0x33FF3B30 }
        c.drawRect(0f, 0f, width.toFloat(), height * SafeZone.TOP, shade)
        c.drawRect(0f, height * (1 - SafeZone.BOTTOM), width.toFloat(), height.toFloat(), shade)
        c.drawRect(width * (1 - SafeZone.RIGHT_ACTIONS), height * SafeZone.TOP, width.toFloat(), height * (1 - SafeZone.BOTTOM), shade)
        val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xCCFFFFFF.toInt(); strokeWidth = 3 * u; pathEffect = DashPathEffect(floatArrayOf(18 * u, 12 * u), 0f) }
        c.drawRect(width * SafeZone.SIDE, height * SafeZone.TOP, width * (1 - SafeZone.RIGHT_ACTIONS), height * (1 - SafeZone.BOTTOM), guide)
    }

    // ---- easing ----------------------------------------------------------------------------
    private fun easeInOut(x: Float) = if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2).let { it * it } / 2
    private fun easeOut(x: Float) = 1 - (1 - x) * (1 - x)
    private fun easeOutBack(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1; return 1 + c3 * (x - 1).let { it * it * it } + c1 * (x - 1).let { it * it } }
}
