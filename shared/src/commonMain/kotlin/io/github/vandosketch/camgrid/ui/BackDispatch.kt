package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Composable

/**
 * A function that sends Back as if the platform's Back key had been pressed, so it reaches the
 * innermost enabled [BackHandler] (say the view editor's mode before the app's screen change).
 * For keys and buttons the platform does not treat as Back by itself (Esc, Backspace, the
 * mouse's back button).
 */
@Composable
internal expect fun rememberBackDispatcher(): () -> Unit
