package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTest {

    private val grid = "rtsp://192.0.2.10:554/sub"
    private val detail = "rtsp://192.0.2.10:554/main"

    @Test
    fun fullscreenUrl_usesDetailUrlWhenSet() {
        val camera = Camera(id = "c1", name = "Front", gridUrl = grid, detailUrl = detail)
        assertEquals(detail, camera.fullscreenUrl)
    }

    @Test
    fun fullscreenUrl_fallsBackToGridUrlWhenDetailUrlEmpty() {
        val camera = Camera(id = "c1", name = "Front", gridUrl = grid, detailUrl = "")
        assertEquals(grid, camera.fullscreenUrl)
    }

    @Test
    fun fullscreenUrl_fallsBackToGridUrlWhenDetailUrlDefaulted() {
        val camera = Camera(id = "c1", name = "Front", gridUrl = grid)
        assertEquals(grid, camera.fullscreenUrl)
    }

    @Test
    fun fullscreenUrl_fallsBackToGridUrlWhenDetailUrlIsWhitespace() {
        val camera = Camera(id = "c1", name = "Front", gridUrl = grid, detailUrl = "  \t ")
        assertEquals(grid, camera.fullscreenUrl)
    }

    @Test
    fun fullscreenUrl_trimsDetailUrl() {
        val camera = Camera(id = "c1", name = "Front", gridUrl = grid, detailUrl = "  $detail \n")
        assertEquals(detail, camera.fullscreenUrl)
    }

    @Test
    fun fullscreenUrl_trimsGridUrlOnFallback() {
        val camera = Camera(id = "c1", name = "Front", gridUrl = " $grid  ", detailUrl = " ")
        assertEquals(grid, camera.fullscreenUrl)
    }

    @Test
    fun camGridConfig_defaults() {
        val config = CamGridConfig()
        assertEquals(listOf(CamView.uniform(CamGridConfig.DEFAULT_VIEW_ID, "", 2, 2)), config.views)
        assertEquals(emptyList<Camera>(), config.cameras)
        assertEquals("", config.go2rtcBaseUrl)
        assertEquals(CamGridConfig.CURRENT_VERSION, config.version)
        assertEquals(2, CamGridConfig.CURRENT_VERSION)
    }
}
