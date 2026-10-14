package com.aaforge.shesafe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ObsidianColorScheme = darkColorScheme(
    primary = ObsidianColors.AccentViolet,
    background = ObsidianColors.Background,
    surface = ObsidianColors.SurfaceCard,
    onBackground = ObsidianColors.TextPrimary,
    onSurface = ObsidianColors.TextPrimary
)

@Composable
fun SheSafeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ObsidianColorScheme,
        content = content
    )
}
