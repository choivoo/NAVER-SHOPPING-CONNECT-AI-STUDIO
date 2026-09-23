package com.shoppingconnect.aistudio.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.shoppingconnect.aistudio.domain.model.Severity
import com.shoppingconnect.aistudio.ui.theme.LocalExtraColors
import com.shoppingconnect.aistudio.ui.theme.LocalReduceMotion
import java.io.File

@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
        actions = actions,
    )
}

@Composable
fun CenterTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) }, actions = actions)
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), modifier = modifier) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), maxLines = 1)
    }
}

@Composable
fun Dot(color: Color, size: Int = 10) { Box(Modifier.size(size.dp).clip(CircleShape).background(color)) }

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String? = null, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
    }
}

/** Human-readable error with the recovery actions that fit it. */
@Composable
fun ErrorPanel(message: String, onRetry: (() -> Unit)? = null, secondaryLabel: String? = null, onSecondary: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRetry != null) Button(onClick = onRetry) { Text("다시 시도") }
                if (secondaryLabel != null && onSecondary != null) OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

@Composable
fun SeverityIcon(s: Severity) {
    val extra = LocalExtraColors.current
    when (s) {
        Severity.ERROR -> Icon(Icons.Default.ErrorOutline, "오류", tint = MaterialTheme.colorScheme.error)
        Severity.WARNING -> Icon(Icons.Default.Warning, "경고", tint = extra.warning)
        Severity.INFO -> Icon(Icons.Default.Info, "정보", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun CheckRow(ok: Boolean, text: String, detail: String? = null) {
    val extra = LocalExtraColors.current
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = if (ok) "통과" else "확인 필요", tint = if (ok) extra.success else extra.warning)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun KeyValue(key: String, value: String?, unknown: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(
            if (unknown || value.isNullOrBlank()) "unknown (확인 불가)" else value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (unknown || value.isNullOrBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun FileImage(path: String?, modifier: Modifier = Modifier, contentDescription: String? = null, crop: Boolean = true) {
    if (path == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.outline)
        }
    } else {
        AsyncImage(model = File(path), contentDescription = contentDescription, modifier = modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
    }
}

/** Small "spark" indicator used while AI works (static when Reduce Motion is on). */
@Composable
fun SparkIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val reduce = LocalReduceMotion.current
    val angle = if (reduce) 0f else {
        val t = rememberInfiniteTransition(label = "spark")
        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "angle")
        a
    }
    Canvas(modifier.size(28.dp).rotate(angle).semantics { contentDescription = "진행 중" }) {
        val c = center; val r = size.minDimension / 2
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, c.y - r); lineTo(c.x + r * 0.22f, c.y - r * 0.22f); lineTo(c.x + r, c.y); lineTo(c.x + r * 0.22f, c.y + r * 0.22f)
            lineTo(c.x, c.y + r); lineTo(c.x - r * 0.22f, c.y + r * 0.22f); lineTo(c.x - r, c.y); lineTo(c.x - r * 0.22f, c.y - r * 0.22f); close()
        }
        drawPath(path, color)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "확인", destructive: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Text(
            "DEMO — 템플릿으로 만든 예시 결과입니다. 실제 AI 생성 결과가 아니며 게시용이 아닙니다.",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun ScoreRing(score: Int, label: String, modifier: Modifier = Modifier) {
    val extra = LocalExtraColors.current
    val color = when { score >= 80 -> extra.success; score >= 60 -> extra.warning; else -> MaterialTheme.colorScheme.error }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(84.dp).semantics(mergeDescendants = true) { contentDescription = "$label $score 점" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) {
            drawArc(track, 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(10.dp.toPx()))
            drawArc(color, -90f, 360f * score / 100f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String = { "%.2f".format(it) }, steps: Int = 0, onChange: (Float) -> Unit) {
    Column {
        Row { Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)); Text(format(value), style = MaterialTheme.typography.labelMedium) }
        androidx.compose.material3.Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
