package com.shoppingconnect.aistudio.ui.adaptive

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/** Compact = cover screen / phone, Medium = small tablet / landscape, Expanded = Fold inner screen / tablet. */
enum class LayoutClass { COMPACT, MEDIUM, EXPANDED }

data class FoldInfo(
    val hasHinge: Boolean = false,
    val separating: Boolean = false,
    val vertical: Boolean = true,
    val halfOpened: Boolean = false,
    val hingeStartPx: Int = 0,
    val hingeEndPx: Int = 0,
)

data class WindowLayout(val layoutClass: LayoutClass, val fold: FoldInfo, val widthDp: Int) {
    val isCompact get() = layoutClass == LayoutClass.COMPACT
    val isExpanded get() = layoutClass == LayoutClass.EXPANDED
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout(LayoutClass.COMPACT, FoldInfo(), 360) }

/** Reads size class + folding feature. Recomposes on fold/unfold without recreating the Activity. */
@Composable
fun rememberWindowLayout(): WindowLayout {
    val info = currentWindowAdaptiveInfo()
    val sc = info.windowSizeClass
    val cls = when {
        sc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> LayoutClass.EXPANDED
        sc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> LayoutClass.MEDIUM
        else -> LayoutClass.COMPACT
    }
    val activity = LocalContext.current as? Activity
    var fold by remember { mutableStateOf(FoldInfo()) }
    LaunchedEffect(activity) {
        activity ?: return@LaunchedEffect
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { layout ->
            val f = layout.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            fold = if (f == null) FoldInfo() else FoldInfo(
                hasHinge = true, separating = f.isSeparating, vertical = f.orientation == FoldingFeature.Orientation.VERTICAL,
                halfOpened = f.state == FoldingFeature.State.HALF_OPENED,
                hingeStartPx = if (f.orientation == FoldingFeature.Orientation.VERTICAL) f.bounds.left else f.bounds.top,
                hingeEndPx = if (f.orientation == FoldingFeature.Orientation.VERTICAL) f.bounds.right else f.bounds.bottom,
            )
        }
    }
    val widthDp = with(LocalDensity.current) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }
    return WindowLayout(cls, fold, widthDp)
}

/**
 * Production workspace: LEFT (navigation/assets) · CENTER (main) · RIGHT (inspector).
 * - Compact (cover screen): only CENTER; side panes are opened from the screen's own sheets.
 * - Medium: CENTER + RIGHT.
 * - Expanded (Fold inner): all three. A separating vertical hinge is never covered by content —
 *   the split is snapped to the hinge and the hinge width is left empty.
 */
@Composable
fun WorkspacePanes(
    left: (@Composable () -> Unit)?,
    center: @Composable () -> Unit,
    right: (@Composable () -> Unit)?,
    leftWidth: Dp = 280.dp,
    rightWidth: Dp = 360.dp,
    modifier: Modifier = Modifier,
) {
    val wl = LocalWindowLayout.current
    val density = LocalDensity.current
    Row(modifier.fillMaxSize()) {
        when (wl.layoutClass) {
            LayoutClass.COMPACT -> Box(Modifier.weight(1f).fillMaxHeight()) { center() }
            LayoutClass.MEDIUM -> {
                Box(Modifier.weight(1f).fillMaxHeight()) { center() }
                if (right != null) { VerticalDivider(); Box(Modifier.width(rightWidth.coerceAtMost(320.dp)).fillMaxHeight()) { right() } }
            }
            LayoutClass.EXPANDED -> {
                val hinge = wl.fold.takeIf { it.hasHinge && it.separating && it.vertical }
                if (hinge != null) {
                    // Book posture with a physical gap: left half = left+center, right half = right pane.
                    val leftHalf = with(density) { hinge.hingeStartPx.toDp() }
                    val gap = with(density) { (hinge.hingeEndPx - hinge.hingeStartPx).toDp() }
                    Row(Modifier.width(leftHalf).fillMaxHeight()) {
                        if (left != null) { Box(Modifier.width(leftWidth.coerceAtMost(leftHalf / 3)).fillMaxHeight()) { left() }; VerticalDivider() }
                        Box(Modifier.weight(1f).fillMaxHeight()) { center() }
                    }
                    Spacer(Modifier.width(gap))
                    Box(Modifier.weight(1f).fillMaxHeight()) { right?.invoke() ?: Unit }
                } else {
                    if (left != null) { Box(Modifier.width(leftWidth).fillMaxHeight()) { left() }; VerticalDivider() }
                    Box(Modifier.weight(1f).fillMaxHeight()) { center() }
                    if (right != null) { VerticalDivider(); Box(Modifier.width(rightWidth).fillMaxHeight()) { right() } }
                }
            }
        }
    }
}
