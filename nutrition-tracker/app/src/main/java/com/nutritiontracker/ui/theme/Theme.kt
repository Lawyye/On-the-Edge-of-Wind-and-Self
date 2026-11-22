package com.nutritiontracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Цветовые токены. Экраны используют только их, а не конкретные цвета, —
 * поэтому светлая тема добавляется одним новым набором токенов.
 */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentMuted: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val protein: Color,
    val fat: Color,
    val carbs: Color,
    val fiber: Color,
    val water: Color,
    val isDark: Boolean,
)

val DarkAppColors = AppColors(
    background = Color(0xFF101114),
    surface = Color(0xFF191B20),
    surfaceVariant = Color(0xFF22252B),
    surfaceHigh = Color(0xFF2A2D34),
    outline = Color(0xFF30333B),
    textPrimary = Color(0xFFF3F3F5),
    textSecondary = Color(0xFF9296A0),
    textTertiary = Color(0xFF686C76),
    accent = Color(0xFFA78BFA),
    onAccent = Color(0xFF16121F),
    accentMuted = Color(0x29A78BFA),
    success = Color(0xFF65C99A),
    warning = Color(0xFFE6B45C),
    error = Color(0xFFEF7474),
    protein = Color(0xFFA78BFA),
    fat = Color(0xFFE59D7A),
    carbs = Color(0xFF6FB1E6),
    fiber = Color(0xFF8FCFC0),
    water = Color(0xFF6FB1E6),
    isDark = true,
)

@Immutable
data class AppType(
    val hero: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val number: TextStyle,
    val numberLarge: TextStyle,
)

private const val TABULAR = "tnum"

val DefaultAppType = AppType(
    hero = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR),
    title = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    section = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    body = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyStrong = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
    caption = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    label = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    number = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR),
    numberLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp, fontFeatureSettings = TABULAR),
)

object Dimens {
    val screenPadding = 16.dp
    val cardPadding = 16.dp
    val gap = 12.dp
    val gapSmall = 8.dp
    val gapTiny = 4.dp
    val radiusCard = 20.dp
    val radiusControl = 14.dp
    val radiusChip = 10.dp
    val touchTarget = 48.dp
}

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
val LocalAppType = staticCompositionLocalOf { DefaultAppType }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppType
        @Composable @ReadOnlyComposable get() = LocalAppType.current
}

@Composable
fun NutritionTheme(colors: AppColors = DarkAppColors, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accentMuted,
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.accent,
        onSecondary = colors.onAccent,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.textSecondary,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surfaceVariant,
        surfaceContainerHighest = colors.surfaceHigh,
        surfaceContainerLow = colors.surface,
        surfaceContainerLowest = colors.background,
        outline = colors.outline,
        outlineVariant = colors.outline,
        error = colors.error,
        onError = colors.onAccent,
    )
    val type = DefaultAppType
    val typography = Typography(
        headlineMedium = type.title,
        titleLarge = type.title,
        titleMedium = type.section,
        bodyLarge = type.body,
        bodyMedium = type.caption,
        labelLarge = type.bodyStrong,
        labelMedium = type.label,
    )
    val shapes = Shapes(
        small = RoundedCornerShape(Dimens.radiusChip),
        medium = RoundedCornerShape(Dimens.radiusControl),
        large = RoundedCornerShape(Dimens.radiusCard),
        extraLarge = RoundedCornerShape(28.dp),
    )
    CompositionLocalProvider(LocalAppColors provides colors, LocalAppType provides type) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
