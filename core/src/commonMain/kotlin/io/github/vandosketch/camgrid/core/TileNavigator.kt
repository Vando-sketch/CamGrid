package io.github.vandosketch.camgrid.core

import kotlin.math.abs

enum class Direction { UP, DOWN, LEFT, RIGHT }

/**
 * D-pad navigation between tiles of any shape, for the Fire TV remote.
 *
 * Within a page it picks the nearest tile in the pressed direction, preferring tiles that share
 * a row (for LEFT/RIGHT) or column (for UP/DOWN) with the current one. Empty tiles are skipped.
 * LEFT/RIGHT past the edge go to the previous/next page and land on the tile nearest that edge
 * at the same height. UP/DOWN never change page. With nowhere to go the position stays.
 */
object TileNavigator {

    fun move(pages: List<GridPage>, position: GridPosition, direction: Direction): GridPosition {
        val page = pages.getOrNull(position.page) ?: return position
        val tiles = page.tiles
        if (position.index !in tiles.indices) return position
        val current = rect(page.view, tiles[position.index].tile)

        val focusable = tiles.indices.filter { tiles[it].camera != null }
        nearest(current, focusable.map { it to rect(page.view, tiles[it].tile) }, position.index, direction)
            ?.let { return GridPosition(position.page, it) }

        val targetPage = when (direction) {
            Direction.LEFT -> position.page - 1
            Direction.RIGHT -> position.page + 1
            else -> return position
        }
        val target = pages.getOrNull(targetPage) ?: return position
        val entry = target.tiles.indices
            .filter { target.tiles[it].camera != null }
            .minWithOrNull(
                compareBy<Int> { -overlap(current.top, current.bottom, rect(target.view, target.tiles[it].tile).let { r -> r.top to r.bottom }) }
                    .thenBy { abs(rect(target.view, target.tiles[it].tile).centerY - current.centerY) }
                    .thenBy {
                        val r = rect(target.view, target.tiles[it].tile)
                        if (direction == Direction.RIGHT) r.left else -r.right
                    }
                    .thenBy { it },
            ) ?: return position
        return GridPosition(targetPage, entry)
    }

    /**
     * The tile index next to [from] in [direction] among [tiles], by geometry only (for the
     * layout editor, where every tile is selectable). Null when there is none.
     */
    fun neighbor(tiles: List<Tile>, from: Int, direction: Direction): Int? {
        val current = tiles.getOrNull(from) ?: return null
        return nearest(cellRect(current), tiles.indices.map { it to cellRect(tiles[it]) }, from, direction)
    }

    private data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
    }

    /** Position on the screen as fractions 0..1, so views with different canvases compare. */
    private fun rect(view: CamView, tile: Tile) = Rect(
        tile.x.toDouble() / view.columns,
        tile.y.toDouble() / view.rows,
        tile.right.toDouble() / view.columns,
        tile.bottom.toDouble() / view.rows,
    )

    private fun cellRect(tile: Tile) =
        Rect(tile.x.toDouble(), tile.y.toDouble(), tile.right.toDouble(), tile.bottom.toDouble())

    private fun overlap(a1: Double, a2: Double, b: Pair<Double, Double>) =
        (minOf(a2, b.second) - maxOf(a1, b.first)).coerceAtLeast(0.0)

    private fun nearest(current: Rect, candidates: List<Pair<Int, Rect>>, self: Int, direction: Direction): Int? {
        val eps = 1e-9
        val ahead = candidates.filter { (index, r) ->
            index != self && when (direction) {
                Direction.LEFT -> r.right <= current.left + eps
                Direction.RIGHT -> r.left >= current.right - eps
                Direction.UP -> r.bottom <= current.top + eps
                Direction.DOWN -> r.top >= current.bottom - eps
            }
        }
        val horizontal = direction == Direction.LEFT || direction == Direction.RIGHT
        fun gap(r: Rect) = when (direction) {
            Direction.LEFT -> current.left - r.right
            Direction.RIGHT -> r.left - current.right
            Direction.UP -> current.top - r.bottom
            Direction.DOWN -> r.top - current.bottom
        }
        fun sideOverlap(r: Rect) =
            if (horizontal) overlap(current.top, current.bottom, r.top to r.bottom)
            else overlap(current.left, current.right, r.left to r.right)
        fun sideDistance(r: Rect) =
            if (horizontal) abs(r.centerY - current.centerY) else abs(r.centerX - current.centerX)

        val inLine = ahead.filter { sideOverlap(it.second) > eps }
        val pool = inLine.ifEmpty { ahead }
        return pool.minWithOrNull(
            compareBy<Pair<Int, Rect>> { gap(it.second) }
                .thenBy { -sideOverlap(it.second) }
                .thenBy { sideDistance(it.second) }
                .thenBy { it.first },
        )?.first
    }
}
