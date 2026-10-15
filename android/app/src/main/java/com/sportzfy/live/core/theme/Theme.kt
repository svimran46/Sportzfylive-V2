package com.sportzfy.live.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SportzfyColorScheme = darkColorScheme(
    primary = SportzfyColors.Rose,
    onPrimary = SportzfyColors.TextPrimary,
    primaryContainer = SportzfyColors.RoseDim,
    secondary = SportzfyColors.Emerald,
    onSecondary = SportzfyColors.Charcoal,
    background = SportzfyColors.Charcoal,
    onBackground = SportzfyColors.TextPrimary,
    surface = SportzfyColors.Slate,
    onSurface = SportzfyColors.TextPrimary,
    surfaceVariant = SportzfyColors.SlateRaised,
    onSurfaceVariant = SportzfyColors.TextSecondary,
    outline = SportzfyColors.Outline,
)

/** The app is dark-only by design; there is no light scheme. */
@Composable
fun SportzfyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SportzfyColorScheme,
        typography = SportzfyTypography,
        shapes = SportzfyShapes,
        content = content,
    )
}
