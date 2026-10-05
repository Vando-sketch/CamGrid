package io.github.vandosketch.camgrid.core

/** Splits cameras into grid pages of `layout.tilesPerPage` cameras each. */
object GridPaging {

    /** Cameras chunked into pages in config order. No cameras: an empty list. */
    fun pages(config: CamGridConfig): List<List<Camera>> = TODO()

    /** Number of pages; 0 when there are no cameras. */
    fun pageCount(config: CamGridConfig): Int = TODO()

    /** The page that shows the camera with [cameraId], or null if there is no such camera. */
    fun pageOf(config: CamGridConfig, cameraId: String): Int? = TODO()
}

enum class Direction { UP, DOWN, LEFT, RIGHT }

/** A focused tile: [page] index and [index] of the tile within that page (row-major). */
data class GridPosition(val page: Int, val index: Int)

/**
 * D-pad navigation across grid pages, for the Fire TV remote.
 *
 * Rules:
 * - UP/DOWN move one row within the page; at the top/bottom edge, or when the target tile
 *   does not exist (last page partly filled), the position stays the same.
 * - LEFT/RIGHT move one column within the row. At the left edge, LEFT goes to the previous
 *   page (same row, last column); at the right edge, or when the next tile in the row does not
 *   exist, RIGHT goes to the next page (same row, first column). On the target page the index
 *   is clamped to that page's last tile. With no previous/next page the position stays.
 */
object GridNavigator {
    fun move(position: GridPosition, direction: Direction, layout: GridLayout, cameraCount: Int): GridPosition = TODO()
}
