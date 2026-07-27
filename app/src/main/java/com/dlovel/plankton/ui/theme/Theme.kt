
package com.dlovel.plankton.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun PlanktonManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
