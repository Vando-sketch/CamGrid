package io.github.vandosketch.camgrid.desktop

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import io.github.vandosketch.camgrid.desktop.config.SavedWindow
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopWindowTest {

    @Test
    fun floatingWindowSavesItsBounds() {
        val state = WindowState(position = WindowPosition(30.dp, 40.dp), size = DpSize(1000.dp, 700.dp))
        assertEquals(SavedWindow(1000, 700, 30, 40, WindowPlacement.Floating), state.toSaved(previous = null))
    }

    @Test
    fun fullscreenKeepsTheFloatingBounds() {
        val floating = SavedWindow(1000, 700, 30, 40, WindowPlacement.Floating)
        val state = WindowState(
            placement = WindowPlacement.Fullscreen,
            position = WindowPosition(0.dp, 0.dp),
            size = DpSize(1920.dp, 1080.dp),
        )
        assertEquals(floating.copy(placement = WindowPlacement.Fullscreen), state.toSaved(previous = floating))
    }

    @Test
    fun centredWindowHasNoPosition() {
        val state = WindowState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(1280.dp, 800.dp))
        assertEquals(SavedWindow(1280, 800, null, null, WindowPlacement.Floating), state.toSaved(previous = null))
    }

    @Test
    fun toggleFullscreenGoesBackToFloating() {
        val state = WindowState()
        state.toggleFullscreen()
        assertEquals(WindowPlacement.Fullscreen, state.placement)
        state.toggleFullscreen()
        assertEquals(WindowPlacement.Floating, state.placement)
    }
}
