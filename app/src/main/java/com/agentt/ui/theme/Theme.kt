package com.agentt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = TintPrimary,
    onPrimary = TintOnPrimary,
    primaryContainer = TintPrimaryContainer,
    onPrimaryContainer = TintOnPrimaryContainer,
    secondary = TintOnSurfaceVariant,
    secondaryContainer = TintPrimaryContainer,
    background = TintBackground,
    surface = TintSurface,
    surfaceVariant = TintCardLight,
    onSurface = TintOnSurface,
    onSurfaceVariant = TintOnSurfaceVariant,
    outline = TintOutlineVariant,
    outlineVariant = TintOutlineVariant,
    error = TintErrorLight,
    errorContainer = TintErrorContainerLight,
    onErrorContainer = TintOnErrorContainerLight
)

private val DarkColors = darkColorScheme(
    primary = TintPrimaryDark,
    onPrimary = TintOnPrimaryDark,
    primaryContainer = Color(0xFF33447A),
    onPrimaryContainer = TintPrimaryContainer,
    secondary = TintOutlineVariant,
    secondaryContainer = Color(0xFF33447A),
    background = TintBackgroundDark,
    surface = TintSurfaceDark,
    surfaceVariant = TintCardDark,
    onSurface = Color.White,
    onSurfaceVariant = TintOutlineVariant,
    outline = Color(0xFF45464F),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun AgentTTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
