package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.Test

class TileNavigatorTest {

    private fun cam(id: String) = Camera(id = id, name = id, gridUrl = "rtsp://192.0.2.10:554/$id")

    private fun pages(count: Int, vararg views: CamView) = ViewPaging.pages(
        CamGridConfig(views = views.toList(), cameras = (1..count).map { cam("c$it") }),
    )

    private fun pos(page: Int, index: Int) = GridPosition(page, index)

    /**
     * 3x2 uniform grid, 6 tiles per page:
     *
     *     0 1 2
     *     3 4 5
     */
    private val uniform = CamView.uniform("u", "", 3, 2)

    private fun moveUniform(page: Int, index: Int, direction: Direction, cameras: Int = 12) =
        TileNavigator.move(pages(cameras, uniform), pos(page, index), direction)

    @Test
    fun uniform_movesWithinPage() {
        assertEquals(pos(0, 1), moveUniform(0, 4, Direction.UP))
        assertEquals(pos(0, 4), moveUniform(0, 1, Direction.DOWN))
        assertEquals(pos(0, 3), moveUniform(0, 4, Direction.LEFT))
        assertEquals(pos(0, 5), moveUniform(0, 4, Direction.RIGHT))
    }

    @Test
    fun uniform_upAndDownStopAtEdges() {
        assertEquals(pos(1, 0), moveUniform(1, 0, Direction.UP))
        assertEquals(pos(0, 4), moveUniform(0, 4, Direction.DOWN))
    }

    @Test
    fun uniform_leftRightCrossPagesInSameRow() {
        assertEquals(pos(1, 3), moveUniform(0, 5, Direction.RIGHT))
        assertEquals(pos(0, 2), moveUniform(1, 0, Direction.LEFT))
        assertEquals(pos(0, 5), moveUniform(1, 3, Direction.LEFT))
    }

    @Test
    fun uniform_stopsAtFirstAndLastPage() {
        assertEquals(pos(0, 0), moveUniform(0, 0, Direction.LEFT))
        assertEquals(pos(1, 5), moveUniform(1, 5, Direction.RIGHT))
    }

    @Test
    fun uniform_emptyTilesAreSkipped() {
        // 8 cameras: page 1 has tiles 0 and 1 only.
        assertEquals(pos(1, 1), moveUniform(1, 1, Direction.RIGHT, cameras = 8))
        assertEquals(pos(1, 1), moveUniform(1, 1, Direction.DOWN, cameras = 8))
        // From the bottom row onto a page whose bottom row is empty: the closest tile.
        assertEquals(pos(1, 0), moveUniform(0, 5, Direction.RIGHT, cameras = 8))
    }

    @Test
    fun uniform_downIntoPartlyFilledRowTakesNearestTile() {
        // 4 cameras on one page: tile 3 sits only below tile 0.
        assertEquals(pos(0, 3), moveUniform(0, 1, Direction.DOWN, cameras = 4))
    }

    /**
     * The kitchen example on a 4x2 canvas:
     *
     *     0 1 2 2
     *     0 1 3 3
     */
    private val kitchen = CamView(
        "k", "", 4, 2,
        listOf(Tile(0, 0, 1, 2), Tile(1, 0, 1, 2), Tile(2, 0, 2, 1), Tile(2, 1, 2, 1)),
    )

    private fun moveKitchen(index: Int, direction: Direction) =
        TileNavigator.move(pages(4, kitchen), pos(0, index), direction)

    @Test
    fun asymmetric_moves() {
        assertEquals(pos(0, 1), moveKitchen(0, Direction.RIGHT))
        assertEquals(pos(0, 2), moveKitchen(1, Direction.RIGHT))
        assertEquals(pos(0, 1), moveKitchen(2, Direction.LEFT))
        assertEquals(pos(0, 1), moveKitchen(3, Direction.LEFT))
        assertEquals(pos(0, 3), moveKitchen(2, Direction.DOWN))
        assertEquals(pos(0, 2), moveKitchen(3, Direction.UP))
        assertEquals(pos(0, 0), moveKitchen(0, Direction.DOWN))
    }

    @Test
    fun asymmetric_crossesIntoNextViewNearestToTheRow() {
        val second = CamView.uniform("u", "", 2, 2)
        val all = pages(4, kitchen, second)
        // From the bottom-right tile into the next view: its bottom-left tile.
        assertEquals(pos(1, 2), TileNavigator.move(all, pos(0, 3), Direction.RIGHT))
        assertEquals(pos(1, 0), TileNavigator.move(all, pos(0, 2), Direction.RIGHT))
        // Back from the next view's top-left tile: the right-most tile in that row.
        assertEquals(pos(0, 2), TileNavigator.move(all, pos(1, 0), Direction.LEFT))
    }

    @Test
    fun neighbor_ignoresCamerasAndUsesOnlyGeometry() {
        // The layout editor moves between tiles whether or not a camera is assigned.
        assertEquals(3, TileNavigator.neighbor(kitchen.tiles, 2, Direction.DOWN))
        assertEquals(null, TileNavigator.neighbor(kitchen.tiles, 0, Direction.LEFT))
    }
}
