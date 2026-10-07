package io.github.vandosketch.camgrid.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint

// The keys shared by the remote, a keyboard and the shortcuts help (ShortcutsDialog lists them).

/** OK on the remote, Enter on a keyboard. */
internal fun Key.isConfirm(): Boolean = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

/** 1 to 9 for the digit keys (main row and number pad), null for any other key. */
internal fun Key.digit(): Int? = when (this) {
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

/**
 * +1 for the "next page" keys (Page Down, channel up like the next TV channel, next track), -1
 * for the previous ones, 0 for any other key.
 */
internal fun Key.pageDelta(): Int = when (this) {
    Key.PageDown, Key.ChannelUp, Key.MediaNext -> 1
    Key.PageUp, Key.ChannelDown, Key.MediaPrevious -> -1
    else -> 0
}

/**
 * Esc and Backspace act as Back. Not Android's Back key: that already reaches BackHandler
 * through the system, and handling it here too would go back twice.
 */
internal fun Key.isBackShortcut(): Boolean = this == Key.Escape || this == Key.Backspace

/** ? (whatever the layout puts it on), F1 or a Help key opens the shortcuts help. */
internal fun KeyEvent.isHelpShortcut(): Boolean =
    key == Key.F1 || key == Key.Help || utf16CodePoint == '?'.code
