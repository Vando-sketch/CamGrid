package io.github.vandosketch.camgrid.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// The activity's OnBackPressedDispatcher, the one BackHandler listens to on Android.
@Composable
internal actual fun rememberBackDispatcher(): () -> Unit {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    return remember(dispatcher) { { dispatcher?.onBackPressed() } }
}
