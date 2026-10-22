package io.github.vandosketch.camgrid.desktop

import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.ViewPaging
import io.github.vandosketch.camgrid.desktop.config.DesktopDevicePreferences
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecondWindowTest {

    private val dir: Path = Files.createTempDirectory("camgrid-second-window-test")
    private val preferences = DesktopDevicePreferences(dir.resolve(DesktopDevicePreferences.FILE_NAME))

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private val cameras = (1..5).map { Camera("cam$it", "Cam $it", "rtsp://192.0.2.10:8554/cam$it") }

    /** "all": 2x1 auto tiles over the five cameras (three pages); "porch": one page. */
    private val config = CamGridConfig(
        views = listOf(CamView.uniform("all", "All", 2, 1), CamView.uniform("porch", "Porch", 1, 1).let { porch ->
            porch.copy(tiles = porch.tiles.map { it.copy(camera = "cam1") })
        }),
        cameras = cameras,
    )
    private val pages = ViewPaging.pages(config)

    @Test
    fun offUntilTurnedOnAndRemembered() {
        assertFalse(SecondWindow.isEnabled(preferences))
        SecondWindow.setEnabled(preferences, true)
        assertTrue(SecondWindow.isEnabled(DesktopDevicePreferences(dir.resolve(DesktopDevicePreferences.FILE_NAME))))
        SecondWindow.setEnabled(preferences, false)
        assertFalse(SecondWindow.isEnabled(preferences))
    }

    @Test
    fun aNewSecondWindowStartsOnTheSecondView() {
        assertEquals(listOf("all", "all", "all", "porch"), pages.map { it.view.id })
        assertEquals(3, SecondWindow.startPage(preferences, pages))
    }

    @Test
    fun withASingleViewItStartsOnTheFirstPage() {
        val single = ViewPaging.pages(config.copy(views = config.views.take(1)))
        assertEquals(0, SecondWindow.startPage(single, viewId = null, pageInView = null))
    }

    @Test
    fun theSavedViewAndPageComeBackEvenWhenViewsMoved() {
        SecondWindow.save(preferences, pages[2])
        assertEquals(2, SecondWindow.startPage(preferences, pages))
        // Porch moved to the front: "all" page 2 is now the fourth page.
        val moved = ViewPaging.pages(config.copy(views = config.views.reversed()))
        assertEquals(3, SecondWindow.startPage(preferences, moved))
    }

    @Test
    fun fewerPagesNowGiveTheViewsLastPage() {
        SecondWindow.save(preferences, pages[2])
        val fewer = ViewPaging.pages(config.copy(cameras = cameras.take(3)))
        assertEquals(1, SecondWindow.startPage(preferences, fewer))
    }

    @Test
    fun aDeletedViewFallsBackToTheSecondView() {
        assertEquals(3, SecondWindow.startPage(pages, viewId = "gone", pageInView = 0))
    }
}
