package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun gridLayout_defaultsToTwoByTwo() {
        val layout = GridLayout()
        assertEquals(2, layout.columns)
        assertEquals(2, layout.rows)
        assertEquals(4, layout.tilesPerPage)
    }

    @Test
    fun gridLayout_tilesPerPageIsColumnsTimesRows() {
        assertEquals(1, GridLayout(1, 1).tilesPerPage)
        assertEquals(6, GridLayout(3, 2).tilesPerPage)
        assertEquals(12, GridLayout(4, 3).tilesPerPage)
        assertEquals(16, GridLayout(4, 4).tilesPerPage)
    }

    @Test
    fun gridLayout_acceptsBounds() {
        GridLayout(GridLayout.MIN_SIZE, GridLayout.MIN_SIZE)
        GridLayout(GridLayout.MAX_SIZE, GridLayout.MAX_SIZE)
        GridLayout(1, 4)
        GridLayout(4, 1)
    }

    @Test
    fun gridLayout_boundsConstants() {
        assertEquals(1, GridLayout.MIN_SIZE)
        assertEquals(4, GridLayout.MAX_SIZE)
    }

    @Test
    fun gridLayout_rejectsZeroColumns() {
        assertThrows(IllegalArgumentException::class.java) { GridLayout(0, 2) }
    }

    @Test
    fun gridLayout_rejectsZeroRows() {
        assertThrows(IllegalArgumentException::class.java) { GridLayout(2, 0) }
    }

    @Test
    fun gridLayout_rejectsFiveColumns() {
        assertThrows(IllegalArgumentException::class.java) { GridLayout(5, 2) }
    }

    @Test
    fun gridLayout_rejectsFiveRows() {
        assertThrows(IllegalArgumentException::class.java) { GridLayout(2, 5) }
    }

    @Test
    fun gridLayout_rejectsNegative() {
        assertThrows(IllegalArgumentException::class.java) { GridLayout(-1, 2) }
        assertThrows(IllegalArgumentException::class.java) { GridLayout(2, -1) }
    }

    @Test
    fun camGridConfig_defaults() {
        val config = CamGridConfig()
        assertEquals(GridLayout(), config.layout)
        assertEquals(emptyList<Camera>(), config.cameras)
        assertEquals("", config.go2rtcBaseUrl)
        assertEquals(CamGridConfig.CURRENT_VERSION, config.version)
        assertEquals(1, CamGridConfig.CURRENT_VERSION)
    }
}
