package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.Test

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
        assertFailsWith<IllegalArgumentException> { CamView.uniform("v", "", 5, 4) }
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
        assertFailsWith<IllegalArgumentException> { view(2, 2, Tile(0, 0, 2, 2), Tile(1, 1)) }
        assertFailsWith<IllegalArgumentException> { view(2, 2, Tile(0, 0), Tile(0, 0)) }
    }

    @Test
    fun rejectsTilesOutsideTheCanvas() {
        assertFailsWith<IllegalArgumentException> { view(2, 2, Tile(2, 0)) }
        assertFailsWith<IllegalArgumentException> { view(2, 2, Tile(0, 1, 1, 2)) }
        assertFailsWith<IllegalArgumentException> { view(2, 2, Tile(1, 0, 2, 1)) }
    }

    @Test
    fun rejectsBadTileGeometry() {
        assertFailsWith<IllegalArgumentException> { Tile(-1, 0) }
        assertFailsWith<IllegalArgumentException> { Tile(0, -1) }
        assertFailsWith<IllegalArgumentException> { Tile(0, 0, 0, 1) }
        assertFailsWith<IllegalArgumentException> { Tile(0, 0, 1, 0) }
    }

    @Test
    fun rejectsCanvasOutsideBounds() {
        assertFailsWith<IllegalArgumentException> { view(0, 1) }
        assertFailsWith<IllegalArgumentException> { view(1, 0) }
        assertFailsWith<IllegalArgumentException> { view(CamView.MAX_CELLS + 1, 1) }
        view(CamView.MAX_CELLS, CamView.MAX_CELLS)
    }

    @Test
    fun rejectsTooManyTiles() {
        val tiles = (0 until CamView.MAX_TILES + 1).map { Tile(it % 12, it / 12) }
        assertFailsWith<IllegalArgumentException> { view(12, 2, *tiles.toTypedArray()) }
    }

    @Test
    fun rejectsBlankId() {
        assertFailsWith<IllegalArgumentException> { CamView(" ", "", 1, 1, emptyList()) }
    }

    @Test
    fun overlaps() {
        assertTrue(Tile(0, 0, 2, 2).overlaps(Tile(1, 1)))
        assertFalse(Tile(0, 0, 2, 2).overlaps(Tile(2, 0)))
        assertFalse(Tile(0, 0, 2, 1).overlaps(Tile(0, 1, 2, 1)))
    }

    @Test
    fun config_rejectsNoViewsAndDuplicateIds() {
        assertFailsWith<IllegalArgumentException> { CamGridConfig(views = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            CamGridConfig(views = listOf(CamView.uniform("a", "", 1, 1), CamView.uniform("a", "", 2, 2)))
        }
    }
}
