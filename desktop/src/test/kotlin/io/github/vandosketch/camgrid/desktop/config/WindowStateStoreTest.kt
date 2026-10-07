package io.github.vandosketch.camgrid.desktop.config

import androidx.compose.ui.window.WindowPlacement
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowStateStoreTest {

    private val dir: Path = Files.createTempDirectory("camgrid-window-test")
    private val file: Path = dir.resolve("sub").resolve(WindowStateStore.FILE_NAME)
    private val store = WindowStateStore(file)

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun savedWindowComesBack() {
        val window = SavedWindow(width = 1600, height = 900, x = 40, y = -20, placement = WindowPlacement.Fullscreen)
        store.save(window)
        assertTrue(file.exists())
        assertEquals(window, WindowStateStore(file).load())
    }

    @Test
    fun positionIsOptional() {
        val window = SavedWindow(width = 1280, height = 800, x = null, y = null, placement = WindowPlacement.Maximized)
        store.save(window)
        assertEquals(window, store.load())
    }

    @Test
    fun saveReplacesTheOldState() {
        store.save(SavedWindow(1280, 800, 0, 0, WindowPlacement.Floating))
        store.save(SavedWindow(1000, 700, 10, 10, WindowPlacement.Maximized))
        assertEquals(SavedWindow(1000, 700, 10, 10, WindowPlacement.Maximized), store.load())
    }

    @Test
    fun nothingSavedGivesNull() {
        assertNull(store.load())
    }

    @Test
    fun damagedFileGivesNull() {
        Files.createDirectories(file.parent)
        file.writeText("width=wide\nheight=800\n")
        assertNull(store.load())
        file.writeText("\u0000\u0001garbage")
        assertNull(store.load())
    }

    @Test
    fun tinySizesAreNotRestored() {
        Files.createDirectories(file.parent)
        file.writeText("width=12\nheight=800\nplacement=Floating\n")
        assertNull(store.load())
    }

    @Test
    fun unknownPlacementIsFloating() {
        Files.createDirectories(file.parent)
        file.writeText("width=1280\nheight=800\nplacement=Sideways\n")
        assertEquals(SavedWindow(1280, 800, null, null, WindowPlacement.Floating), store.load())
    }

    @Test
    fun saveToAnUnwritablePlaceDoesNotThrow() {
        Files.createDirectories(dir.resolve("blocked"))
        // A directory where the file should be: the save fails quietly.
        Files.createDirectories(dir.resolve("blocked").resolve(WindowStateStore.FILE_NAME))
        WindowStateStore(dir.resolve("blocked").resolve(WindowStateStore.FILE_NAME))
            .save(SavedWindow(1280, 800, 0, 0, WindowPlacement.Floating))
    }

    @Test
    fun visibleOnlyWhenTheTitleBarIsOnAScreen() {
        val screens = listOf(Rectangle(0, 0, 1920, 1080), Rectangle(1920, 0, 1280, 1024))
        assertTrue(SavedWindow(1280, 800, 100, 100, WindowPlacement.Floating).isVisibleOn(screens))
        assertTrue(SavedWindow(1280, 800, 2000, 50, WindowPlacement.Floating).isVisibleOn(screens))
        // The second monitor was unplugged.
        assertFalse(SavedWindow(1280, 800, 3300, 50, WindowPlacement.Floating).isVisibleOn(screens))
        // Mostly off the left edge, but a grabbable part of the title bar is still visible.
        assertTrue(SavedWindow(1280, 800, -1100, 10, WindowPlacement.Floating).isVisibleOn(screens))
        // Title bar above the top of every screen.
        assertFalse(SavedWindow(1280, 800, 100, -500, WindowPlacement.Floating).isVisibleOn(screens))
        // No position: the window is centred, always visible.
        assertTrue(SavedWindow(1280, 800, null, null, WindowPlacement.Floating).isVisibleOn(screens))
    }
}
