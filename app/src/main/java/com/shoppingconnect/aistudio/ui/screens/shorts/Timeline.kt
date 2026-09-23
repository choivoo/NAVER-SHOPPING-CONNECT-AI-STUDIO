package com.shoppingconnect.aistudio.ui.screens.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.domain.model.TransitionType

private val LABEL_W = 64.dp
private val TRACK_H = 34.dp

/**
 * Multi-track timeline: Video/Image · Transition · Text · Subtitle · Voice · Music · SFX · Sticker.
 * Pinch to zoom, tap/drag the ruler to move the playhead, tap a clip to select it.
 */
@Composable
fun TimelineView(
    t: ShortTimeline, playheadMs: Long, zoom: Float, selection: Sel?,
    onSeek: (Long) -> Unit, onSelect: (Sel) -> Unit, onZoom: (Float) -> Unit, modifier: Modifier = Modifier,
) {
    val dpPerSec: Dp = (48 * zoom).dp
    val totalW = dpPerSec * (t.durationMs / 1000f + 1f)
    val density = LocalDensity.current
    fun x(ms: Long): Dp = dpPerSec * (ms / 1000f)
    fun msAt(px: Float): Long = with(density) { ((px / dpPerSec.toPx()) * 1000).toLong() }
    val starts = t.sceneStarts()
    val scroll = rememberScrollState()
    val primary = MaterialTheme.colorScheme.primary

    Row(modifier.fillMaxWidth().pointerInput(zoom) { detectTransformGestures { _, _, z, _ -> if (z != 1f) onZoom(zoom * z) } }) {
        Column(Modifier.width(LABEL_W)) {
            Box(Modifier.height(22.dp))
            listOf("영상", "전환", "텍스트", "자막", "음성", "음악", "효과음", "스티커").forEach {
                Box(Modifier.height(TRACK_H).fillMaxWidth().padding(start = 6.dp), contentAlignment = Alignment.CenterStart) { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Column(Modifier.width(totalW)) {
                // Ruler (tap / drag = scrub)
                Box(
                    Modifier.height(22.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pointerInput(dpPerSec) { detectTapGestures { onSeek(msAt(it.x)) } }
                        .pointerInput(dpPerSec) { detectHorizontalDragGestures { change, _ -> onSeek(msAt(change.position.x)) } }
                        .semantics { contentDescription = "타임라인 눈금. 탭하거나 끌어서 재생 위치 이동" },
                ) {
                    val step = when { zoom >= 4f -> 1; zoom >= 1.5f -> 2; zoom >= 0.6f -> 5; else -> 10 }
                    var sec = 0
                    while (sec * 1000 <= t.durationMs) {
                        Text("${sec}s", fontSize = 9.sp, modifier = Modifier.offset(x = x(sec * 1000L) + 2.dp))
                        sec += step
                    }
                }
                Track {
                    t.scenes.forEachIndexed { i, s ->
                        Clip(x(starts[i]), x(s.durationMs), if (s.isVideo) "🎬 ${s.purpose}" else s.purpose.ifBlank { "장면 ${i + 1}" }, Color(0xFF5B4CF0), selection == Sel.SceneSel(i)) { onSelect(Sel.SceneSel(i)) }
                    }
                }
                Track {
                    t.scenes.forEachIndexed { i, s ->
                        if (i > 0 && s.transitionIn.type != TransitionType.NONE) Clip(x(starts[i]), x(s.transitionIn.clampedMs).coerceAtLeast(10.dp), "⇄", Color(0xFFB2572A), false) { onSelect(Sel.SceneSel(i)) }
                    }
                }
                Track { t.texts.forEach { c -> Clip(x(c.startMs), x(c.endMs - c.startMs), c.text, Color(0xFF00897B), selection == Sel.TextSel(c.id)) { onSelect(Sel.TextSel(c.id)) } } }
                Track { if (t.subtitlesEnabled) t.subtitles.forEach { c -> Clip(x(c.startMs), x(c.endMs - c.startMs), c.text, Color(0xFF455A64), selection == Sel.SubSel(c.id)) { onSelect(Sel.SubSel(c.id)) } } }
                Track { t.voiceClips.forEach { c -> Clip(x(c.startMs), x(c.durationMs), "🎙 ${c.label}", Color(0xFFD81B60), selection == Sel.Voice) { onSelect(Sel.Voice) } } }
                Track { if (t.music != null || t.bgmMood != com.shoppingconnect.aistudio.domain.model.BgmMood.NONE) Clip(0.dp, x(t.durationMs), "♪ ${t.music?.label ?: "AI BGM · ${t.bgmMood.label}"}", Color(0xFF6D4C41), selection == Sel.Music) { onSelect(Sel.Music) } }
                Track { t.sfx.forEach { c -> Clip(x(c.startMs), 28.dp, c.sfx?.label ?: "SFX", Color(0xFFF9A825), selection == Sel.SfxSel(c.id)) { onSelect(Sel.SfxSel(c.id)) } } }
                Track { t.stickers.forEach { c -> Clip(x(c.startMs), x(c.endMs - c.startMs), c.emoji, Color(0xFF8E24AA), selection == Sel.StickerSel(c.id)) { onSelect(Sel.StickerSel(c.id)) } } }
            }
            Box(Modifier.offset(x = x(playheadMs)).width(2.dp).fillMaxHeight().background(primary))
        }
    }
}

@Composable
private fun Track(content: @Composable () -> Unit) {
    Box(Modifier.height(TRACK_H).fillMaxWidth().padding(vertical = 3.dp)) { content() }
}

@Composable
private fun Clip(x: Dp, w: Dp, label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.offset(x = x).width(w.coerceAtLeast(6.dp)).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.85f))
            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(onClickLabel = "$label 선택", onClick = onClick).padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
