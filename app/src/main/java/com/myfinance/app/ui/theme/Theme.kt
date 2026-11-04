package com.myfinance.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val Purple = Color(0xFF6554E8)

data class ExtraColors(val income: Color, val expense: Color)

val LocalExtra = staticCompositionLocalOf { ExtraColors(Color(0xFF16A34A), Color(0xFFE5484D)) }

val MaterialTheme.extra: ExtraColors
    @Composable @ReadOnlyComposable get() = LocalExtra.current

private val LightColors = lightColorScheme(
    primary = Purple, onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E6FD), onPrimaryContainer = Color(0xFF2B2170),
    secondary = Color(0xFF8B7CF6), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEDFC), onSecondaryContainer = Color(0xFF2B2170),
    background = Color(0xFFF7F7FC), onBackground = Color(0xFF1C1B2E),
    surface = Color.White, onSurface = Color(0xFF1C1B2E),
    surfaceVariant = Color(0xFFF0EFFA), onSurfaceVariant = Color(0xFF6B6A80),
    outline = Color(0xFFD9D7EC), outlineVariant = Color(0xFFE8E6F5),
    error = Color(0xFFE5484D), errorContainer = Color(0xFFFDE8E8), onErrorContainer = Color(0xFF8A1C20)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9B8FFF), onPrimary = Color(0xFF1B1250),
    primaryContainer = Color(0xFF3A2FA0), onPrimaryContainer = Color(0xFFE9E6FD),
    secondary = Color(0xFFB0A6FF), onSecondary = Color(0xFF1B1250),
    secondaryContainer = Color(0xFF2E2A52), onSecondaryContainer = Color(0xFFE9E6FD),
    background = Color(0xFF111120), onBackground = Color(0xFFEDEBFA),
    surface = Color(0xFF1B1B2D), onSurface = Color(0xFFEDEBFA),
    surfaceVariant = Color(0xFF2A2942), onSurfaceVariant = Color(0xFFA9A7C2),
    outline = Color(0xFF444260), outlineVariant = Color(0xFF33324A),
    error = Color(0xFFFF6B6B), errorContainer = Color(0xFF4A2024), onErrorContainer = Color(0xFFFFDAD9)
)

@Composable
fun MyFinanceTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val extra = if (dark) ExtraColors(Color(0xFF4ADE80), Color(0xFFFF6B6B)) else ExtraColors(Color(0xFF16A34A), Color(0xFFE5484D))
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(
            colorScheme = colors,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp)
            ),
            content = content
        )
    }
}
