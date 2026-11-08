package com.textura.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
    primary = Color(0xFFFF7A59),
    onPrimary = Color(0xFF2A0F08),
    primaryContainer = Color(0xFF5A2417),
    onPrimaryContainer = Color(0xFFFFDAD0),
    secondary = Color(0xFF9DBDFF),
    onSecondary = Color(0xFF0B2250),
    tertiary = Color(0xFFF3D27A),
    background = Color(0xFF14141A),
    onBackground = Color(0xFFEDEBF2),
    surface = Color(0xFF1B1B22),
    onSurface = Color(0xFFEDEBF2),
    surfaceVariant = Color(0xFF282832),
    onSurfaceVariant = Color(0xFFB9B7C4),
    surfaceContainer = Color(0xFF1F1F27),
    surfaceContainerHigh = Color(0xFF262630),
    surfaceContainerHighest = Color(0xFF2E2E39),
    outline = Color(0xFF5A5A69),
    outlineVariant = Color(0xFF3A3A47),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFD9482B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD0),
    onPrimaryContainer = Color(0xFF3B0D03),
    secondary = Color(0xFF3558A8),
    tertiary = Color(0xFF8A6A12),
    background = Color(0xFFF8F5F0),
    onBackground = Color(0xFF1B1B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B20),
    surfaceVariant = Color(0xFFECE8E1),
    onSurfaceVariant = Color(0xFF55535C),
    surfaceContainer = Color(0xFFF2EEE8),
    surfaceContainerHigh = Color(0xFFECE8E1),
    surfaceContainerHighest = Color(0xFFE5E0D8),
    outline = Color(0xFF8A8793),
    outlineVariant = Color(0xFFD3CEC6),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
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
        content = content,
    )
}
