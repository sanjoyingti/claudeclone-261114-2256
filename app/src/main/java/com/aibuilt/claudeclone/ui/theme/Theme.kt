package com.aibuilt.claudeclone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ClaudeOrange,
    onPrimary = ClaudeSurfaceLight,
    primaryContainer = ClaudeBubbleUserLight,
    onPrimaryContainer = ClaudeTextLight,
    background = ClaudeBgLight,
    onBackground = ClaudeTextLight,
    surface = ClaudeBgLight,
    onSurface = ClaudeTextLight,
    surfaceVariant = ClaudeBubbleUserLight,
    onSurfaceVariant = ClaudeMutedLight,
    outline = ClaudeOutlineLight,
    error = ClaudeError
)

private val DarkColors = darkColorScheme(
    primary = ClaudeOrange,
    onPrimary = ClaudeSurfaceDark,
    primaryContainer = ClaudeBubbleUserDark,
    onPrimaryContainer = ClaudeTextDark,
    background = ClaudeBgDark,
    onBackground = ClaudeTextDark,
    surface = ClaudeBgDark,
    onSurface = ClaudeTextDark,
    surfaceVariant = ClaudeBubbleUserDark,
    onSurfaceVariant = ClaudeMutedDark,
    outline = ClaudeOutlineDark,
    error = ClaudeError
)

@Composable
fun ClaudeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ClaudeTypography,
        content = content
    )
}
