package com.shoppingconnect.aistudio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoppingconnect.aistudio.data.settings.ThemeMode

/** Brand palette — an original violet/teal identity (not NAVER's brand colours). */
object Brand {
    val Violet = Color(0xFF5B4CF0)
    val VioletDark = Color(0xFFB8AEFF)
    val Teal = Color(0xFF00B89F)
    val TealDark = Color(0xFF4FE3CB)
    val Spark = Color(0xFFFFB020)
    /** Used only to signal "NAVER connected" state. */
    val Connected = Color(0xFF14A04B)
    val Warning = Color(0xFFE08A00)
    val Error = Color(0xFFD93A3A)
    val Success = Color(0xFF14A04B)
}

private val Light = lightColorScheme(
    primary = Brand.Violet, onPrimary = Color.White, primaryContainer = Color(0xFFE6E2FF), onPrimaryContainer = Color(0xFF1B1066),
    secondary = Brand.Teal, onSecondary = Color.White, secondaryContainer = Color(0xFFC9F5EC), onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFB2572A), tertiaryContainer = Color(0xFFFFDCCB),
    background = Color(0xFFFAF9FF), onBackground = Color(0xFF16151D), surface = Color(0xFFFAF9FF), onSurface = Color(0xFF16151D),
    surfaceVariant = Color(0xFFE7E5F2), onSurfaceVariant = Color(0xFF48465A), surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F2FC), surfaceContainer = Color(0xFFEEECF8), surfaceContainerHigh = Color(0xFFE8E6F3), surfaceContainerHighest = Color(0xFFE2E0ED),
    outline = Color(0xFF7A778C), outlineVariant = Color(0xFFCAC7DA), error = Brand.Error,
)

private val Dark = darkColorScheme(
    primary = Brand.VioletDark, onPrimary = Color(0xFF2A1B8F), primaryContainer = Color(0xFF3F31C4), onPrimaryContainer = Color(0xFFE6E2FF),
    secondary = Brand.TealDark, onSecondary = Color(0xFF003730), secondaryContainer = Color(0xFF005045), onSecondaryContainer = Color(0xFFC9F5EC),
    tertiary = Color(0xFFFFB690), tertiaryContainer = Color(0xFF8C3F14),
    background = Color(0xFF111018), onBackground = Color(0xFFE7E4F0), surface = Color(0xFF111018), onSurface = Color(0xFFE7E4F0),
    surfaceVariant = Color(0xFF46445A), onSurfaceVariant = Color(0xFFC9C6DA), surfaceContainerLowest = Color(0xFF0C0B12),
    surfaceContainerLow = Color(0xFF191822), surfaceContainer = Color(0xFF1E1D28), surfaceContainerHigh = Color(0xFF282733), surfaceContainerHighest = Color(0xFF33323E),
    outline = Color(0xFF938FA6), outlineVariant = Color(0xFF46445A), error = Color(0xFFFF8A80),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp),
)

private val base = Typography()
val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

data class ExtraColors(val success: Color, val warning: Color, val connected: Color, val spark: Color)

val LocalExtraColors = staticCompositionLocalOf { ExtraColors(Brand.Success, Brand.Warning, Brand.Connected, Brand.Spark) }
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun AiStudioTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamic: Boolean = false, reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

val MonoStyle = TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
