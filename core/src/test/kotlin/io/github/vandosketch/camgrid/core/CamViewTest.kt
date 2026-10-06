package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CamViewTest {

    private fun view(columns: Int, rows: Int, vararg tiles: Tile) = CamView("v", "", columns, rows, tiles.toList())

    @Test
    fun uniform_buildsRowMajorAutoTiles() {
        val view = CamView.uniform("v", "Name", 3, 2)
        assertEquals(3, view.columns)
        assertEquals(2, view.rows)
        assertEquals(
            listOf(Tile(0, 0), Tile(1, 0), Tile(2, 0), Tile(0, 1), Tile(1, 1), Tile(2, 1)),
            view.tiles,
        )
        assertTrue(view.tiles.all { it.camera == null && it.fit == FitMode.FIT })
    }

    @Test
    fun uniform_rejectsMoreThanMaxTiles() {
        assertThrows(IllegalArgumentException::class.java) { CamView.uniform("v", "", 5, 4) }
    }

    @Test
    fun tilesMayLeaveCellsEmpty() {
        view(4, 2, Tile(0, 0, 1, 2))
    }

    @Test
    fun acceptsTheKitchenExample() {
        // Two portrait tiles side by side, then two landscape tiles stacked.
        view(4, 2, Tile(0, 0, 1, 2), Tile(1, 0, 1, 2), Tile(2, 0, 2, 1), Tile(2, 1, 2, 1))
    }

    @Test
    fun rejectsOverlap() {
        assertThrows(IllegalArgumentException::class.java) { view(2, 2, Tile(0, 0, 2, 2), Tile(1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { view(2, 2, Tile(0, 0), Tile(0, 0)) }
    }

    @Test
    fun rejectsTilesOutsideTheCanvas() {
        assertThrows(IllegalArgumentException::class.java) { view(2, 2, Tile(2, 0)) }
        assertThrows(IllegalArgumentException::class.java) { view(2, 2, Tile(0, 1, 1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { view(2, 2, Tile(1, 0, 2, 1)) }
    }

    @Test
    fun rejectsBadTileGeometry() {
        assertThrows(IllegalArgumentException::class.java) { Tile(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { Tile(0, -1) }
        assertThrows(IllegalArgumentException::class.java) { Tile(0, 0, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { Tile(0, 0, 1, 0) }
    }

    @Test
    fun rejectsCanvasOutsideBounds() {
        assertThrows(IllegalArgumentException::class.java) { view(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { view(1, 0) }
        assertThrows(IllegalArgumentException::class.java) { view(CamView.MAX_CELLS + 1, 1) }
        view(CamView.MAX_CELLS, CamView.MAX_CELLS)
    }

    @Test
    fun rejectsTooManyTiles() {
        val tiles = (0 until CamView.MAX_TILES + 1).map { Tile(it % 12, it / 12) }
        assertThrows(IllegalArgumentException::class.java) { view(12, 2, *tiles.toTypedArray()) }
    }

    @Test
    fun rejectsBlankId() {
        assertThrows(IllegalArgumentException::class.java) { CamView(" ", "", 1, 1, emptyList()) }
    }

    @Test
    fun overlaps() {
        assertTrue(Tile(0, 0, 2, 2).overlaps(Tile(1, 1)))
        assertFalse(Tile(0, 0, 2, 2).overlaps(Tile(2, 0)))
        assertFalse(Tile(0, 0, 2, 1).overlaps(Tile(0, 1, 2, 1)))
    }

    @Test
    fun config_rejectsNoViewsAndDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) { CamGridConfig(views = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            CamGridConfig(views = listOf(CamView.uniform("a", "", 1, 1), CamView.uniform("a", "", 2, 2)))
        }
    }
}
