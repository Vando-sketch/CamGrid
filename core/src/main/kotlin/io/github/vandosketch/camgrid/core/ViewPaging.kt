package io.github.vandosketch.camgrid.core

/** A tile with the camera it shows, or null for an empty tile. */
data class PlacedTile(val tile: Tile, val camera: Camera?)

/**
 * One screenful: a [view] with cameras assigned to its tiles. A view with more cameras than auto
 * tiles spreads over [pagesInView] pages; this is page [pageInView] of those.
 */
data class GridPage(
    val view: CamView,
    val pageInView: Int,
    val pagesInView: Int,
    val tiles: List<PlacedTile>,
) {
    /** Live streams this page plays at once: one per tile with a camera. */
    val streamCount: Int get() = tiles.count { it.camera != null }
}

/** A focused tile: [page] index and [index] of the tile within that page's tiles. */
data class GridPosition(val page: Int, val index: Int)

/**
 * Turns the config's views into pages.
 *
 * Per view: a tile with a camera id always shows that camera (empty if the camera no longer
 * exists). Auto tiles take the cameras not fixed in that view, in camera order; when there are
 * more cameras than auto tiles, the view repeats on further pages with the fixed tiles unchanged.
 * A uniform view of auto tiles therefore pages exactly like the old columns x rows grid.
 */
object ViewPaging {

    fun pages(config: CamGridConfig): List<GridPage> {
        val byId = config.cameras.associateBy { it.id }
        return config.views.flatMap { view -> pagesOf(view, config.cameras, byId) }
    }

    private fun pagesOf(view: CamView, cameras: List<Camera>, byId: Map<String, Camera>): List<GridPage> {
        val fixedIds = view.tiles.mapNotNullTo(HashSet()) { it.camera }
        val autoCount = view.tiles.count { it.camera == null }
        val pool = cameras.filterNot { it.id in fixedIds }
        val pageCount = if (autoCount == 0 || pool.isEmpty()) 1 else (pool.size + autoCount - 1) / autoCount
        return (0 until pageCount).map { page ->
            var next = page * autoCount
            val placed = view.tiles.map { tile ->
                val camera = if (tile.camera != null) byId[tile.camera] else pool.getOrNull(next++)
                PlacedTile(tile, camera)
            }
            GridPage(view, page, pageCount, placed)
        }
    }

    /**
     * Where [cameraId] is shown: on [preferPage] if it is there, otherwise its first appearance.
     * Null when no page shows it.
     */
    fun locate(pages: List<GridPage>, cameraId: String, preferPage: Int): GridPosition? {
        fun indexOn(page: Int) = pages.getOrNull(page)?.tiles?.indexOfFirst { it.camera?.id == cameraId } ?: -1
        indexOn(preferPage).let { if (it >= 0) return GridPosition(preferPage, it) }
        for (page in pages.indices) {
            val index = indexOn(page)
            if (index >= 0) return GridPosition(page, index)
        }
        return null
    }
}
