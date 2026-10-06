package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ViewEditorTest {

    private fun view(columns: Int, rows: Int, vararg tiles: Tile) = CamView("v", "Name", columns, rows, tiles.toList())

    // moveTile

    @Test
    fun moveTile_movesWhenTheSpaceIsFree() {
        val start = view(3, 1, Tile(0, 0))
        assertEquals(listOf(Tile(1, 0)), ViewEditor.moveTile(start, 0, 1, 0).tiles)
    }

    @Test
    fun moveTile_blockedByEdgeOrOtherTileReturnsSameView() {
        val start = view(3, 1, Tile(0, 0), Tile(1, 0))
        assertSame(start, ViewEditor.moveTile(start, 0, -1, 0))
        assertSame(start, ViewEditor.moveTile(start, 0, 1, 0))
        assertSame(start, ViewEditor.moveTile(start, 1, 0, 1))
    }

    @Test
    fun moveTile_badIndexReturnsSameView() {
        val start = view(3, 1, Tile(0, 0))
        assertSame(start, ViewEditor.moveTile(start, 5, 1, 0))
    }

    // resizeTile

    @Test
    fun resizeTile_growsAndShrinksRightAndBottomEdge() {
        val start = view(4, 2, Tile(0, 0))
        val grown = ViewEditor.resizeTile(start, 0, 1, 1)
        assertEquals(Tile(0, 0, 2, 2), grown.tiles.single())
        assertEquals(Tile(0, 0, 1, 2), ViewEditor.resizeTile(grown, 0, -1, 0).tiles.single())
    }

    @Test
    fun resizeTile_neverBelowOneCellOrIntoAnotherTile() {
        val start = view(3, 1, Tile(0, 0), Tile(2, 0))
        assertSame(start, ViewEditor.resizeTile(start, 0, -1, 0))
        val wider = ViewEditor.resizeTile(start, 0, 1, 0)
        assertEquals(Tile(0, 0, 2, 1), wider.tiles[0])
        assertSame(wider, ViewEditor.resizeTile(wider, 0, 1, 0))
    }

    // add / remove

    @Test
    fun addTile_takesTheFirstFreeCell() {
        val start = view(2, 2, Tile(0, 0, 2, 1))
        val result = ViewEditor.addTile(start)
        assertEquals(Tile(0, 1), result.tiles.last())
    }

    @Test
    fun addTile_fullCanvasReturnsSameView() {
        val start = view(1, 1, Tile(0, 0))
        assertSame(start, ViewEditor.addTile(start))
    }

    @Test
    fun removeTile_removesByIndex() {
        val start = view(2, 1, Tile(0, 0), Tile(1, 0))
        assertEquals(listOf(Tile(1, 0)), ViewEditor.removeTile(start, 0).tiles)
    }

    @Test
    fun fillEmpty_fillsEveryFreeCellRowMajor() {
        val start = view(2, 2, Tile(0, 0, 1, 2))
        assertEquals(listOf(Tile(0, 0, 1, 2), Tile(1, 0), Tile(1, 1)), ViewEditor.fillEmpty(start).tiles)
    }

    @Test
    fun fillEmpty_stopsAtMaxTiles() {
        val start = view(12, 12)
        assertEquals(CamView.MAX_TILES, ViewEditor.fillEmpty(start).tiles.size)
    }

    // tile settings

    @Test
    fun setCameraAndFit() {
        val start = view(2, 1, Tile(0, 0), Tile(1, 0))
        val withCamera = ViewEditor.setTileCamera(start, 1, "door")
        assertEquals("door", withCamera.tiles[1].camera)
        assertEquals(null, ViewEditor.setTileCamera(withCamera, 1, null).tiles[1].camera)
        assertEquals(FitMode.CROP, ViewEditor.setTileFit(start, 0, FitMode.CROP).tiles[0].fit)
    }

    // canvas

    @Test
    fun setCanvas_growKeepsTiles() {
        val start = view(2, 1, Tile(0, 0), Tile(1, 0))
        val result = ViewEditor.setCanvas(start, 4, 2)
        assertEquals(4, result.columns)
        assertEquals(2, result.rows)
        assertEquals(start.tiles, result.tiles)
    }

    @Test
    fun setCanvas_shrinkClipsAndDropsTiles() {
        val start = view(4, 2, Tile(0, 0, 3, 2), Tile(3, 0))
        assertEquals(listOf(Tile(0, 0, 2, 1)), ViewEditor.setCanvas(start, 2, 1).tiles)
    }

    @Test
    fun setCanvas_clampsToBounds() {
        val start = view(2, 2)
        assertEquals(CamView.MIN_CELLS, ViewEditor.setCanvas(start, 0, 0).columns)
        assertEquals(CamView.MAX_CELLS, ViewEditor.setCanvas(start, 99, 99).rows)
    }

    // presets

    @Test
    fun everyPresetIsAValidFullView() {
        for (preset in ViewPreset.entries) {
            val view = preset.build("p", "")
            val area = view.tiles.sumOf { it.w * it.h }
            assertEquals(preset.name, view.columns * view.rows, area)
        }
    }

    @Test
    fun kitchenPreset() {
        val view = ViewPreset.TWO_PORTRAIT_TWO_LANDSCAPE.build("k", "Kitchen")
        assertEquals(4, view.columns)
        assertEquals(2, view.rows)
        assertEquals(
            listOf(Tile(0, 0, 1, 2), Tile(1, 0, 1, 2), Tile(2, 0, 2, 1), Tile(2, 1, 2, 1)),
            view.tiles.map { it.copy(fit = FitMode.FIT) },
        )
        assertEquals(listOf(FitMode.CROP, FitMode.CROP, FitMode.FIT, FitMode.FIT), view.tiles.map { it.fit })
    }

    @Test
    fun applyPreset_keepsIdNameAndCamerasInReadingOrder() {
        val start = view(2, 1, Tile(1, 0, camera = "b"), Tile(0, 0, camera = "a"))
        val result = ViewEditor.applyPreset(start, ViewPreset.TWO_PORTRAIT_TWO_LANDSCAPE)
        assertEquals("v", result.id)
        assertEquals("Name", result.name)
        assertEquals(listOf("a", "b", null, null), result.tiles.map { it.camera })
    }
}
