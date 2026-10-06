package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Most tests use a 3x2 layout (6 tiles per page):
 *
 *     0 1 2
 *     3 4 5
 */
class GridNavigatorTest {

    private val layout3x2 = GridLayout(columns = 3, rows = 2)

    private fun move(page: Int, index: Int, direction: Direction, cameraCount: Int, layout: GridLayout = layout3x2) =
        GridNavigator.move(GridPosition(page, index), direction, layout, cameraCount)

    private fun pos(page: Int, index: Int) = GridPosition(page, index)

    // UP

    @Test
    fun up_movesOneRow() {
        assertEquals(pos(0, 1), move(0, 4, Direction.UP, 12))
        assertEquals(pos(1, 0), move(1, 3, Direction.UP, 12))
    }

    @Test
    fun up_atTopEdgeStays() {
        assertEquals(pos(0, 0), move(0, 0, Direction.UP, 12))
        assertEquals(pos(0, 2), move(0, 2, Direction.UP, 12))
        assertEquals(pos(1, 1), move(1, 1, Direction.UP, 12))
    }

    @Test
    fun up_doesNotChangePage() {
        // Top row of the second page: no move to the previous page.
        assertEquals(pos(1, 0), move(1, 0, Direction.UP, 12))
    }

    // DOWN

    @Test
    fun down_movesOneRow() {
        assertEquals(pos(0, 4), move(0, 1, Direction.DOWN, 12))
        assertEquals(pos(1, 5), move(1, 2, Direction.DOWN, 12))
    }

    @Test
    fun down_atBottomEdgeStays() {
        assertEquals(pos(0, 3), move(0, 3, Direction.DOWN, 12))
        assertEquals(pos(0, 5), move(0, 5, Direction.DOWN, 12))
    }

    @Test
    fun down_doesNotChangePage() {
        assertEquals(pos(0, 4), move(0, 4, Direction.DOWN, 12))
    }

    @Test
    fun down_intoMissingTileOnPartialLastPageStays() {
        // 10 cameras: page 1 has tiles 0..3.
        assertEquals(pos(1, 1), move(1, 1, Direction.DOWN, 10))
        assertEquals(pos(1, 2), move(1, 2, Direction.DOWN, 10))
    }

    @Test
    fun down_intoExistingTileOnPartialLastPageMoves() {
        assertEquals(pos(1, 3), move(1, 0, Direction.DOWN, 10))
    }

    // LEFT

    @Test
    fun left_movesOneColumn() {
        assertEquals(pos(0, 1), move(0, 2, Direction.LEFT, 12))
        assertEquals(pos(0, 4), move(0, 5, Direction.LEFT, 12))
    }

    @Test
    fun left_atLeftEdgeGoesToPreviousPageSameRowLastColumn() {
        assertEquals(pos(0, 2), move(1, 0, Direction.LEFT, 12))
        assertEquals(pos(0, 5), move(1, 3, Direction.LEFT, 12))
    }

    @Test
    fun left_fromPartialLastPageGoesToPreviousPage() {
        // 8 cameras: page 1 has tiles 0..1.
        assertEquals(pos(0, 2), move(1, 0, Direction.LEFT, 8))
        assertEquals(pos(1, 0), move(1, 1, Direction.LEFT, 8))
    }

    @Test
    fun left_atLeftEdgeOfFirstPageStays() {
        assertEquals(pos(0, 0), move(0, 0, Direction.LEFT, 12))
        assertEquals(pos(0, 3), move(0, 3, Direction.LEFT, 12))
    }

    // RIGHT

    @Test
    fun right_movesOneColumn() {
        assertEquals(pos(0, 1), move(0, 0, Direction.RIGHT, 12))
        assertEquals(pos(0, 5), move(0, 4, Direction.RIGHT, 12))
    }

    @Test
    fun right_atRightEdgeGoesToNextPageSameRowFirstColumn() {
        assertEquals(pos(1, 0), move(0, 2, Direction.RIGHT, 12))
        assertEquals(pos(1, 3), move(0, 5, Direction.RIGHT, 12))
    }

    @Test
    fun right_targetIndexClampedToLastTileOfNextPage() {
        // 8 cameras: page 1 has tiles 0..1, so row 1 / column 0 (index 3) does not exist.
        assertEquals(pos(1, 1), move(0, 5, Direction.RIGHT, 8))
        assertEquals(pos(1, 0), move(0, 2, Direction.RIGHT, 8))
    }

    @Test
    fun right_targetIndexClampedOnSingleTileLastPage() {
        // 7 cameras: page 1 has tile 0 only.
        assertEquals(pos(1, 0), move(0, 5, Direction.RIGHT, 7))
        assertEquals(pos(1, 0), move(0, 2, Direction.RIGHT, 7))
    }

    @Test
    fun right_atRightEdgeOfLastPageStays() {
        assertEquals(pos(1, 2), move(1, 2, Direction.RIGHT, 12))
        assertEquals(pos(1, 5), move(1, 5, Direction.RIGHT, 12))
    }

    @Test
    fun right_whenNextTileMissingOnLastPageStays() {
        // 10 cameras: page 1 has tiles 0..3; tile 4 does not exist and there is no next page.
        assertEquals(pos(1, 3), move(1, 3, Direction.RIGHT, 10))
        // 14 cameras: page 2 has tiles 0..1.
        assertEquals(pos(2, 1), move(2, 1, Direction.RIGHT, 14))
    }

    @Test
    fun right_whenNextTileExistsOnPartialLastPageMoves() {
        assertEquals(pos(1, 2), move(1, 1, Direction.RIGHT, 10))
    }

    @Test
    fun right_movesAcrossMiddlePage() {
        // 14 cameras: pages of 6, 6, 2.
        assertEquals(pos(2, 0), move(1, 2, Direction.RIGHT, 14))
        assertEquals(pos(2, 1), move(1, 5, Direction.RIGHT, 14))
        assertEquals(pos(1, 2), move(2, 0, Direction.LEFT, 14))
    }

    // Single page

    @Test
    fun singlePage_leftAndRightAtEdgesStay() {
        val layout = GridLayout(2, 2)
        assertEquals(pos(0, 0), move(0, 0, Direction.LEFT, 4, layout))
        assertEquals(pos(0, 2), move(0, 2, Direction.LEFT, 4, layout))
        assertEquals(pos(0, 1), move(0, 1, Direction.RIGHT, 4, layout))
        assertEquals(pos(0, 3), move(0, 3, Direction.RIGHT, 4, layout))
    }

    @Test
    fun singlePage_partiallyFilled() {
        // 2x2 with 3 cameras: tiles 0, 1, 2.
        val layout = GridLayout(2, 2)
        assertEquals(pos(0, 2), move(0, 2, Direction.RIGHT, 3, layout))
        assertEquals(pos(0, 1), move(0, 1, Direction.DOWN, 3, layout))
        assertEquals(pos(0, 2), move(0, 0, Direction.DOWN, 3, layout))
    }

    @Test
    fun singleTile_allDirectionsStay() {
        val layout = GridLayout(1, 1)
        for (direction in Direction.values()) {
            assertEquals(direction.name, pos(0, 0), move(0, 0, direction, 1, layout))
        }
    }

    // Other layouts

    @Test
    fun oneByOne_leftRightPageThroughCameras() {
        val layout = GridLayout(1, 1)
        assertEquals(pos(1, 0), move(0, 0, Direction.RIGHT, 3, layout))
        assertEquals(pos(2, 0), move(1, 0, Direction.RIGHT, 3, layout))
        assertEquals(pos(2, 0), move(2, 0, Direction.RIGHT, 3, layout))
        assertEquals(pos(0, 0), move(1, 0, Direction.LEFT, 3, layout))
        assertEquals(pos(0, 0), move(0, 0, Direction.LEFT, 3, layout))
        assertEquals(pos(1, 0), move(1, 0, Direction.UP, 3, layout))
        assertEquals(pos(1, 0), move(1, 0, Direction.DOWN, 3, layout))
    }

    @Test
    fun singleColumn_leftRightKeepRow() {
        // 1 column x 2 rows, 4 cameras: two full pages.
        val layout = GridLayout(columns = 1, rows = 2)
        assertEquals(pos(1, 1), move(0, 1, Direction.RIGHT, 4, layout))
        assertEquals(pos(0, 1), move(1, 1, Direction.LEFT, 4, layout))
        assertEquals(pos(0, 1), move(0, 0, Direction.DOWN, 4, layout))
    }

    @Test
    fun singleRow_rightEdgeGoesToNextPage() {
        val layout = GridLayout(columns = 4, rows = 1)
        assertEquals(pos(1, 0), move(0, 3, Direction.RIGHT, 6, layout))
        assertEquals(pos(0, 3), move(1, 0, Direction.LEFT, 6, layout))
        assertEquals(pos(1, 1), move(1, 1, Direction.RIGHT, 6, layout))
    }
}
