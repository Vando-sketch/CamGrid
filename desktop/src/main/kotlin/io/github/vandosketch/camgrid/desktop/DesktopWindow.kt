package io.github.vandosketch.camgrid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import io.github.vandosketch.camgrid.desktop.config.SavedWindow
import io.github.vandosketch.camgrid.desktop.config.WindowStateStore
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull

/** The mouse cursor hides after this long without moving, on the grid and in fullscreen. */
private const val CURSOR_HIDE_MS = 3_000L

/** Resizing and moving fire many changes; the window state is saved once it rests this long. */
private const val SAVE_DELAY_MS = 500L

private val DefaultSize = DpSize(1280.dp, 800.dp)

/** The window as last saved, or a centred default window; a position off every screen is dropped. */
fun initialWindowState(saved: SavedWindow?): WindowState {
    val screens = screenBounds()
    val position = saved
        ?.takeIf { it.x != null && it.y != null && it.isVisibleOn(screens) }
        ?.let { WindowPosition(it.x!!.dp, it.y!!.dp) }
        ?: WindowPosition.Aligned(Alignment.Center)
    return WindowState(
        placement = saved?.placement ?: WindowPlacement.Floating,
        position = position,
        size = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: DefaultSize,
    )
}

/**
 * Saves [state] to [store] whenever it changes (once it has rested a moment) and when the
 * window goes away. Only a floating window's bounds are taken, so leaving fullscreen or
 * maximized, even after a restart, goes back to the window the user had before. [initial] is
 * [state] as created, before the window was shown.
 */
@Composable
fun SaveWindowState(state: WindowState, store: WindowStateStore, initial: SavedWindow) {
    val latest = remember { arrayOf(initial) }
    LaunchedEffect(state, store) {
        snapshotFlow { Triple(state.placement, state.size, state.position) }.collectLatest {
            latest[0] = state.toSaved(previous = latest[0])
            delay(SAVE_DELAY_MS)
            store.save(latest[0])
        }
    }
    // Closing right after a change: the delayed save above never runs.
    DisposableEffect(store) {
        onDispose { store.save(latest[0]) }
    }
}

/** [this] window as a [SavedWindow], keeping [previous]'s bounds while it is not floating. */
fun WindowState.toSaved(previous: SavedWindow?): SavedWindow {
    if (previous != null && placement != WindowPlacement.Floating) return previous.copy(placement = placement)
    val absolute = position as? WindowPosition.Absolute
    return SavedWindow(
        width = size.width.value.roundToInt(),
        height = size.height.value.roundToInt(),
        x = absolute?.x?.value?.roundToInt(),
        y = absolute?.y?.value?.roundToInt(),
        placement = placement,
    )
}

/** Window fullscreen on or off. */
fun WindowState.toggleFullscreen() {
    placement = if (placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
}

/** [key] pressed on its own, without Ctrl, Alt or Meta (so shortcuts like Ctrl+F stay free). */
fun KeyEvent.isPlainPress(key: Key): Boolean =
    type == KeyEventType.KeyDown && this.key == key && !isCtrlPressed && !isAltPressed && !isMetaPressed

/**
 * Hides the mouse cursor over [content] after [CURSOR_HIDE_MS] without movement while
 * [enabled] (the camera wall and fullscreen, where it would sit on top of a video), and shows
 * it again on the next move.
 */
@Composable
fun AutoHideCursor(enabled: Boolean, content: @Composable () -> Unit) {
    var hidden by remember { mutableStateOf(false) }
    val moves = remember { Channel<Unit>(Channel.CONFLATED) }
    LaunchedEffect(enabled) {
        hidden = false
        if (!enabled) return@LaunchedEffect
        while (true) {
            if (withTimeoutOrNull(CURSOR_HIDE_MS) { moves.receive() } == null) {
                hidden = true
                moves.receive()
                hidden = false
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Initial pass: seen before the screens, never consumed.
                        awaitPointerEvent(PointerEventPass.Initial)
                        moves.trySend(Unit)
                    }
                }
            }
            // Overrides the tiles' hand cursor too while hidden.
            .pointerHoverIcon(if (hidden) BlankCursor else PointerIcon.Default, overrideDescendants = hidden),
    ) {
        content()
    }
}

private val BlankCursor: PointerIcon by lazy {
    val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "CamGrid hidden cursor"))
}

/** The bounds of every screen, in the same scaled coordinates as window positions in dp. */
private fun screenBounds(): List<Rectangle> = try {
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
} catch (e: Exception) {
    emptyList()
}
