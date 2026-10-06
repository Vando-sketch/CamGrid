package io.github.vandosketch.camgrid.ui

import androidx.compose.ui.input.key.KeyEvent

// UIKit presses carry no repeat count.
internal actual val KeyEvent.isRepeat: Boolean
    get() = false
