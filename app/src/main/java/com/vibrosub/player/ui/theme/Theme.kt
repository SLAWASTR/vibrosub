package com.vibrosub.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepBackground,
    primaryContainer = SurfaceDarkElevated,
    onPrimaryContainer = ElectricCyan,
    secondary = SubwooferViolet,
    onSecondary = DeepBackground,
    tertiary = BassPunchOrange,
    background = DeepBackground,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder
)

@Composable
fun VibroSubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
