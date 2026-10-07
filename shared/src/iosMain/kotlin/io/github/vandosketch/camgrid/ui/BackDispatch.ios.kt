package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

// An input of its own on the view's navigation event dispatcher, which BackHandler listens to.
@Composable
internal actual fun rememberBackDispatcher(): () -> Unit {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val input = remember { KeyBackInput() }
    DisposableEffect(dispatcher) {
        dispatcher?.addInput(input)
        onDispose { dispatcher?.removeInput(input) }
    }
    return input::back
}

private class KeyBackInput : NavigationEventInput() {
    fun back() = dispatchOnBackCompleted()
}
