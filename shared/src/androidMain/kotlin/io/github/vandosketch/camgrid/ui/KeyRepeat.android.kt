package io.github.vandosketch.camgrid.ui

import androidx.compose.ui.input.key.KeyEvent

internal actual val KeyEvent.isRepeat: Boolean
    get() = nativeKeyEvent.repeatCount > 0
