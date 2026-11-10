package com.textura.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.textura.app.data.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9AA5FF),
    onPrimary = Color(0xFF10143D),
    primaryContainer = Color(0xFF262B63),
    onPrimaryContainer = Color(0xFFDDE0FF),
    secondary = Color(0xFFB4BAD9),
    onSecondary = Color(0xFF1B1F38),
    tertiary = Color(0xFFE8C77A),
    background = Color(0xFF0F0F11),
    onBackground = Color(0xFFEDEDF0),
    surface = Color(0xFF151517),
    onSurface = Color(0xFFEDEDF0),
    surfaceVariant = Color(0xFF232327),
    onSurfaceVariant = Color(0xFFA9A9B3),
    surfaceContainer = Color(0xFF1A1A1D),
    surfaceContainerHigh = Color(0xFF222226),
    surfaceContainerHighest = Color(0xFF2A2A2F),
    outline = Color(0xFF6A6A74),
    outlineVariant = Color(0xFF303036),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4B5BD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E9FD),
    onPrimaryContainer = Color(0xFF181D59),
    secondary = Color(0xFF555B7A),
    tertiary = Color(0xFF8A6A12),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF1A1A1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1E),
    surfaceVariant = Color(0xFFEFEFEC),
    onSurfaceVariant = Color(0xFF63636B),
    surfaceContainer = Color(0xFFF4F4F1),
    surfaceContainerHigh = Color(0xFFEEEEEA),
    surfaceContainerHighest = Color(0xFFE8E8E3),
    outline = Color(0xFF9B9BA3),
    outlineVariant = Color(0xFFE2E2DD),
)

private val AppTypography = Typography().let { d ->
    d.copy(
        headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
        headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TexturaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
