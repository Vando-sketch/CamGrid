package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Composable

/**
 * Calls [onBack] for the Back key or gesture while [enabled]; when disabled (or not in the
 * composition) Back does what it would do otherwise (on Android: leave the app).
 */
@Composable
internal expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
