package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Composable

// The activity's OnBackPressedDispatcher, as the app always used (Fire TV remote's Back key included).
@Composable
internal actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
