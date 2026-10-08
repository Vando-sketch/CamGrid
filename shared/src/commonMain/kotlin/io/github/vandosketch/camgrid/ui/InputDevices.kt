package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * True where the user has a real keyboard and mouse (the desktop app), false on TVs, phones and
 * tablets. Picks keyboard-oriented hints over remote ones and enables mouse-only extras.
 */
val LocalHasKeyboardAndMouse = staticCompositionLocalOf { false }

/**
 * Whether the user is moving around with the keys right now, so focus indicators (the grid's
 * selection ring, [focusBorder]) should show. Only for a keyboard and mouse
 * ([LocalHasKeyboardAndMouse]): Compose Desktop starts in key input mode and a mouse click does
 * not leave it, so its input mode would show the rings to mouse users all the time. This starts
 * off and follows the last input instead: on with the navigation keys (arrows, Tab, Enter, ...),
 * off with a mouse press, wheel or a real move.
 *
 * [CamGridApp] provides one ([LocalKeyboardNavigation]) where there is a keyboard and mouse and
 * none elsewhere: a TV's D-pad users must always see where they are.
 */
@Stable
class KeyboardNavigation(active: Boolean = false) {
    /** Snapshot state: readers recompose or redraw when it changes. */
    var active by mutableStateOf(active)
        internal set

    private var lastPointer: Offset? = null

    /** A key went down; [key] decides whether that is moving around with the keys. */
    fun onKeyDown(key: Key) {
        if (key.isNavigationKey()) active = true
    }

    /**
     * A pointer event at [position] (in a fixed root's coordinates). Compose sends synthetic
     * moves (and enter/exit) at the same position when the content under a resting mouse
     * changes, e.g. after a page switch by key; only a move to a new position counts as using
     * the mouse.
     */
    fun onPointerEvent(type: PointerEventType, position: Offset) {
        val previous = lastPointer
        lastPointer = position
        when (type) {
            PointerEventType.Press, PointerEventType.Scroll -> active = false
            PointerEventType.Move -> if (previous != null && previous != position) active = false
        }
    }
}

/** The [KeyboardNavigation] of a keyboard-and-mouse app; null on TVs, phones and tablets. */
val LocalKeyboardNavigation = staticCompositionLocalOf<KeyboardNavigation?> { null }

/**
 * Whether focus should be visible: [LocalKeyboardNavigation] where there is one, otherwise the
 * platform's key input mode (D-pad on a TV: yes; a touched phone: no).
 */
@Composable
fun isKeyboardNavigation(): Boolean =
    LocalKeyboardNavigation.current?.active ?: (LocalInputModeManager.current.inputMode == InputMode.Keyboard)

/**
 * The keys that move focus or act on it. Letters, Esc, F-keys and shortcuts are not. Also the
 * keys whose first press on the idle grid only brings the faded selection back ([GridScreen]).
 */
internal fun Key.isNavigationKey(): Boolean = when (this) {
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter,
    Key.Tab, Key.Enter, Key.NumPadEnter, Key.Spacebar,
    Key.PageUp, Key.PageDown, Key.MoveHome, Key.MoveEnd,
    -> true
    else -> false
}
