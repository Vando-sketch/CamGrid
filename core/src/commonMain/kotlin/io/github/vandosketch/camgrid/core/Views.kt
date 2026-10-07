package io.github.vandosketch.camgrid.core

import kotlinx.serialization.Serializable

/** How a stream fills a tile whose shape differs from the video's. */
@Serializable
enum class FitMode {
    /** Whole picture visible, black bars where the shapes differ. */
    FIT,

    /** Tile filled completely; the edges of the picture that do not fit are cut off. */
    CROP,
}

/**
 * One rectangle on a [CamView]'s cell canvas: top-left cell ([x], [y]) spanning [w] x [h] cells.
 *
 * [camera] is a camera id. Null makes it an auto tile, filled with the next camera in the
 * config's camera order (see [ViewPaging]). Only the id is stored, never a URL, so a view can
 * be shared or served from elsewhere without carrying credentials.
 */
@Serializable
data class Tile(
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val camera: String? = null,
    val fit: FitMode = FitMode.FIT,
) {
    init {
        require(x >= 0 && y >= 0) { "Tile position must not be negative: ($x, $y)" }
        require(w >= 1 && h >= 1) { "Tile size must be at least 1x1: ${w}x$h" }
    }

    val right: Int get() = x + w
    val bottom: Int get() = y + h

    fun overlaps(other: Tile): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom
}

/**
 * A screen layout: a canvas of [columns] x [rows] equal cells (stretched over the screen) with
 * [tiles] placed on it. Tiles may span cells to form portrait or landscape tiles, and cells may
 * stay empty. [id] is stable and meant to be what a device is told to show.
 *
 * Invalid geometry (outside the canvas, overlapping, too many tiles) throws
 * [IllegalArgumentException], so every existing CamView is drawable.
 */
@Serializable
data class CamView(
    val id: String,
    val name: String = "",
    val columns: Int,
    val rows: Int,
    val tiles: List<Tile>,
) {
    init {
        require(id.isNotBlank()) { "View id must not be blank" }
        require(columns in MIN_CELLS..MAX_CELLS) { "columns must be in $MIN_CELLS..$MAX_CELLS, was $columns" }
        require(rows in MIN_CELLS..MAX_CELLS) { "rows must be in $MIN_CELLS..$MAX_CELLS, was $rows" }
        require(tiles.size <= MAX_TILES) { "At most $MAX_TILES tiles, was ${tiles.size}" }
        tiles.forEachIndexed { i, tile ->
            require(tile.right <= columns && tile.bottom <= rows) { "Tile $i lies outside the ${columns}x$rows canvas" }
            for (j in 0 until i) require(!tile.overlaps(tiles[j])) { "Tiles $j and $i overlap" }
        }
    }

    companion object {
        const val MIN_CELLS = 1
        const val MAX_CELLS = 12

        /** Same as the largest old uniform grid (4x4). More than a stick can decode anyway. */
        const val MAX_TILES = 16

        /** A plain grid of 1x1 auto tiles, row-major: what the old columns x rows setting was. */
        fun uniform(id: String, name: String, columns: Int, rows: Int): CamView = CamView(
            id = id,
            name = name,
            columns = columns,
            rows = rows,
            tiles = (0 until rows).flatMap { y -> (0 until columns).map { x -> Tile(x, y) } },
        )
    }
}

/** Ready-made layouts for the editor. Cell counts are chosen so tiles come out near 16:9 or 9:16 on a TV. */
enum class ViewPreset(val columns: Int, val rows: Int, val tiles: List<Tile>) {
    GRID_2X2(2, 2, uniformTiles(2, 2)),
    GRID_3X3(3, 3, uniformTiles(3, 3)),
    SIDE_BY_SIDE(2, 1, uniformTiles(2, 1)),

    /** Two portrait tiles side by side, then two landscape tiles stacked. */
    TWO_PORTRAIT_TWO_LANDSCAPE(
        4, 2,
        listOf(
            Tile(0, 0, 1, 2, fit = FitMode.CROP),
            Tile(1, 0, 1, 2, fit = FitMode.CROP),
            Tile(2, 0, 2, 1),
            Tile(2, 1, 2, 1),
        ),
    ),

    /** One big tile with three small ones down the right side. */
    ONE_BIG_THREE_SMALL(4, 3, listOf(Tile(0, 0, 3, 3), Tile(3, 0), Tile(3, 1), Tile(3, 2))),

    /** One big tile top-left with five small ones around it. */
    ONE_BIG_FIVE_SMALL(
        3, 3,
        listOf(Tile(0, 0, 2, 2), Tile(2, 0), Tile(2, 1), Tile(0, 2), Tile(1, 2), Tile(2, 2)),
    ),

    /** Three portrait tiles, for doorbell-style cameras. */
    THREE_PORTRAIT(3, 1, (0 until 3).map { Tile(it, 0, fit = FitMode.CROP) }),
    ;

    fun build(id: String, name: String): CamView = CamView(id, name, columns, rows, tiles)
}

private fun uniformTiles(columns: Int, rows: Int) =
    (0 until rows).flatMap { y -> (0 until columns).map { x -> Tile(x, y) } }

/**
 * Pure edits on a [CamView]. Every function returns a valid view; an edit that would break the
 * geometry (leave the canvas, overlap another tile) returns the same instance unchanged, so the
 * editor can just try it.
 */
object ViewEditor {

    fun moveTile(view: CamView, index: Int, dx: Int, dy: Int): CamView {
        val tile = view.tiles.getOrNull(index) ?: return view
        if (tile.x + dx < 0 || tile.y + dy < 0) return view
        return replaceTile(view, index, tile.copy(x = tile.x + dx, y = tile.y + dy))
    }

    /** Moves the right edge by [dw] and the bottom edge by [dh] cells. */
    fun resizeTile(view: CamView, index: Int, dw: Int, dh: Int): CamView {
        val tile = view.tiles.getOrNull(index) ?: return view
        if (tile.w + dw < 1 || tile.h + dh < 1) return view
        return replaceTile(view, index, tile.copy(w = tile.w + dw, h = tile.h + dh))
    }

    /** Adds a 1x1 auto tile in the first free cell (row-major). Full canvas or [CamView.MAX_TILES]: unchanged. */
    fun addTile(view: CamView): CamView {
        if (view.tiles.size >= CamView.MAX_TILES) return view
        val cell = freeCells(view).firstOrNull() ?: return view
        return view.copy(tiles = view.tiles + cell)
    }

    fun removeTile(view: CamView, index: Int): CamView {
        if (index !in view.tiles.indices) return view
        return view.copy(tiles = view.tiles.filterIndexed { i, _ -> i != index })
    }

    /** Fills every free cell with a 1x1 auto tile, row-major, up to [CamView.MAX_TILES]. */
    fun fillEmpty(view: CamView): CamView {
        val room = CamView.MAX_TILES - view.tiles.size
        return view.copy(tiles = view.tiles + freeCells(view).take(room.coerceAtLeast(0)))
    }

    /** [cameraId] null makes the tile an auto tile. */
    fun setTileCamera(view: CamView, index: Int, cameraId: String?): CamView {
        val tile = view.tiles.getOrNull(index) ?: return view
        return replaceTile(view, index, tile.copy(camera = cameraId))
    }

    fun setTileFit(view: CamView, index: Int, fit: FitMode): CamView {
        val tile = view.tiles.getOrNull(index) ?: return view
        return replaceTile(view, index, tile.copy(fit = fit))
    }

    /**
     * Resizes the canvas, clamped to the allowed range. Tiles that start outside the new canvas
     * are removed, tiles that reach past it are cut to fit.
     */
    fun setCanvas(view: CamView, columns: Int, rows: Int): CamView {
        val c = columns.coerceIn(CamView.MIN_CELLS, CamView.MAX_CELLS)
        val r = rows.coerceIn(CamView.MIN_CELLS, CamView.MAX_CELLS)
        val tiles = view.tiles
            .filter { it.x < c && it.y < r }
            .map { it.copy(w = minOf(it.w, c - it.x), h = minOf(it.h, r - it.y)) }
        return view.copy(columns = c, rows = r, tiles = tiles)
    }

    /**
     * Replaces canvas and tiles with [preset]'s, keeping id and name. Cameras of the old tiles
     * are handed to the new tiles in reading order (top to bottom, left to right).
     */
    fun applyPreset(view: CamView, preset: ViewPreset): CamView {
        val cameras = view.tiles.sortedWith(compareBy({ it.y }, { it.x })).map { it.camera }
        val tiles = preset.tiles.mapIndexed { i, tile -> tile.copy(camera = cameras.getOrNull(i)) }
        return CamView(view.id, view.name, preset.columns, preset.rows, tiles)
    }

    private fun replaceTile(view: CamView, index: Int, tile: Tile): CamView = try {
        view.copy(tiles = view.tiles.toMutableList().apply { set(index, tile) })
    } catch (_: IllegalArgumentException) {
        view
    }

    private fun freeCells(view: CamView): List<Tile> =
        (0 until view.rows).flatMap { y -> (0 until view.columns).map { x -> Tile(x, y) } }
            .filter { cell -> view.tiles.none { it.overlaps(cell) } }
}
