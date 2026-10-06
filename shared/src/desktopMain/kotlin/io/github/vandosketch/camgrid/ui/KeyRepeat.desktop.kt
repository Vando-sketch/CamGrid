package io.github.vandosketch.camgrid.ui

import androidx.compose.ui.input.key.KeyEvent

// AWT reports held keys as repeated presses without a repeat count.
internal actual val KeyEvent.isRepeat: Boolean
    get() = false
