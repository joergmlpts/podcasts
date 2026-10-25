package org.joergmlpts.podcasts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF60A5FA),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF0F172A),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF1E3A8A),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDBEAFE),
    secondary = BlueSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightTextSecondary,
    error = DangerRed,
)

@Composable
fun PodcastsTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
