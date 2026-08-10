package com.agentt.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 复刻 TIN 默认色板 (default palette)
private val LightColors = lightColorScheme(
    primary = Color(0xFF4D5C92),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF03174B),
    secondary = Color(0xFF595D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE1F9),
    onSecondaryContainer = Color(0xFF161B2C),
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF75757F),
    outlineVariant = Color(0xFFC6C6D0),
    error = Color(0xFFBB0947),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDDADE),
    onErrorContainer = Color(0xFF400013),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF1D2D61),
    primaryContainer = Color(0xFF354479),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFFC2C5DD),
    onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDEE1F9),
    background = Color(0xFF121213),
    onBackground = Color(0xFFF9F9F9),
    surface = Color(0xFF121213),
    onSurface = Color(0xFFF9F9F9),
    surfaceVariant = Color(0xFF2A2A2C),
    onSurfaceVariant = Color(0xFFCECECE),
    outline = Color(0xFF8E919A),
    outlineVariant = Color(0xFF44464E),
    error = Color(0xFFFCB4BD),
    onError = Color(0xFF670023),
    errorContainer = Color(0xFF910034),
    onErrorContainer = Color(0xFFFCB4BD),
)

@Composable
fun AgentTTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
