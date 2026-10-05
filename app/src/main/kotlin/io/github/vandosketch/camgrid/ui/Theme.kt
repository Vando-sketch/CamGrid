package io.github.vandosketch.camgrid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CamGridColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF002A5C),
    secondaryContainer = Color(0xFF2A3440),
    onSecondaryContainer = Color(0xFFE3E8EF),
    background = Color.Black,
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF1E2329),
    onSurfaceVariant = Color(0xFFB8C0CA),
    error = Color(0xFFFF8A80),
)

/** Always dark: the app runs on wall displays and TVs, usually next to live video. */
@Composable
fun CamGridTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CamGridColors, content = content)
}
