
package com.dlovel.plankton.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val BluePrimary = Color(0xFF12B7F5)
private val BlueDeep = Color(0xFF0EA1D9)
private val BlueSoft = Color(0xFFEAF4FF)
private val Ink = Color(0xFF1B1F24)
private val Muted = Color(0xFF6B7280)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = BlueSoft,
    onPrimaryContainer = Ink,
    secondary = BlueDeep,
    onSecondary = Color.White,
    background = Color(0xFFF4F7FA),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F6FF),
    onSurfaceVariant = Muted,
    outline = Color(0xFFD4E4F7)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF62D4FF),
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF07516B),
    onPrimaryContainer = Color(0xFFB9EBFF),
    secondary = Color(0xFF8ED7F5),
    onSecondary = Color(0xFF003545),
    background = Color(0xFF101417),
    onBackground = Color(0xFFE5F1F5),
    surface = Color(0xFF171D20),
    onSurface = Color(0xFFE5F1F5),
    surfaceVariant = Color(0xFF25343A),
    onSurfaceVariant = Color(0xFFB7C9CE),
    outline = Color(0xFF4C626A)
)

val LocalMotionScale = staticCompositionLocalOf { 1f }

@Composable
fun PlanktonManagerTheme(
    themeMode: String = "SYSTEM",
    animationScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalMotionScale provides animationScale.coerceIn(0f, 1.5f)) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            content = content
        )
    }
}
