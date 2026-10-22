package io.github.vandosketch.camgrid.desktop

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import io.github.vandosketch.camgrid.desktop.config.SavedWindow
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun aNewWindowGoesToTheMiddleOfTheScreenItIsGiven() {
        val screen = Rectangle(1920, 0, 1920, 1080)
        val state = initialWindowState(saved = null, defaultScreen = screen)
        assertEquals(WindowPosition((1920 + 320).dp, 140.dp), state.position)
        assertEquals(DpSize(1280.dp, 800.dp), state.size)
    }

    @Test
    fun aWindowLargerThanTheScreenStartsAtItsCorner() {
        assertEquals(WindowPosition(1920.dp, 0.dp), centredOn(Rectangle(1920, 0, 1024, 600), DpSize(1280.dp, 800.dp)))
    }

    @Test
    fun theFreeScreenIsOneTheOtherWindowDoesNotReach() {
        val left = Rectangle(0, 0, 1920, 1080)
        val right = Rectangle(1920, 0, 2560, 1440)
        assertEquals(right, freeScreen(listOf(left, right), Rectangle(100, 100, 1280, 800)))
        assertEquals(left, freeScreen(listOf(left, right), Rectangle(2000, 0, 2560, 1440)))
        // Spread over both, or only one screen: none is free.
        assertNull(freeScreen(listOf(left, right), Rectangle(1500, 0, 1000, 800)))
        assertNull(freeScreen(listOf(left), Rectangle(100, 100, 1280, 800)))
    }
}
