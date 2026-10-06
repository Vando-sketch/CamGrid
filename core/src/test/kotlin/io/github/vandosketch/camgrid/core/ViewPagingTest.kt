package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewPagingTest {

    private fun cam(id: String) = Camera(id = id, name = id, gridUrl = "rtsp://192.0.2.10:554/$id")

    private fun config(count: Int, vararg views: CamView) = CamGridConfig(
        views = views.toList().ifEmpty { listOf(CamView.uniform("main", "", 2, 2)) },
        cameras = (1..count).map { cam("c$it") },
    )

    /** Camera ids per page, "-" for an empty tile. */
    private fun pageIds(config: CamGridConfig) =
        ViewPaging.pages(config).map { page -> page.tiles.map { it.camera?.id ?: "-" } }

    @Test
    fun uniformView_pagesLikeTheOldGrid() {
        assertEquals(
            listOf(listOf("c1", "c2", "c3", "c4"), listOf("c5", "c6", "-", "-")),
            pageIds(config(6)),
        )
    }

    @Test
    fun noCameras_onePageOfEmptyTiles() {
        assertEquals(listOf(listOf("-", "-", "-", "-")), pageIds(config(0)))
    }

    @Test
    fun pageNumbersWithinView() {
        val pages = ViewPaging.pages(config(9))
        assertEquals(listOf(0, 1, 2), pages.map { it.pageInView })
        assertEquals(listOf(3, 3, 3), pages.map { it.pagesInView })
        assertEquals(listOf(4, 4, 1), pages.map { it.streamCount })
    }

    @Test
    fun fixedTilesShowTheirCameraOnEveryPage() {
        // A big fixed tile for c1, three auto tiles cycling through the rest.
        val view = CamView(
            "v", "", 4, 3,
            listOf(Tile(0, 0, 3, 3, camera = "c1"), Tile(3, 0), Tile(3, 1), Tile(3, 2)),
        )
        assertEquals(
            listOf(listOf("c1", "c2", "c3", "c4"), listOf("c1", "c5", "-", "-")),
            pageIds(config(5, view)),
        )
    }

    @Test
    fun allFixedTiles_onePageAndUnplacedCamerasAreNotShown() {
        val view = CamView("v", "", 2, 1, listOf(Tile(0, 0, camera = "c3"), Tile(1, 0, camera = "c1")))
        assertEquals(listOf(listOf("c3", "c1")), pageIds(config(5, view)))
    }

    @Test
    fun fixedTileWithUnknownCameraIsEmpty() {
        val view = CamView("v", "", 2, 1, listOf(Tile(0, 0, camera = "gone"), Tile(1, 0)))
        assertEquals(listOf(listOf("-", "c1")), pageIds(config(1, view)))
    }

    @Test
    fun viewsFollowEachOther() {
        val a = CamView("a", "", 1, 1, listOf(Tile(0, 0, camera = "c2")))
        val b = CamView.uniform("b", "", 2, 1)
        val pages = ViewPaging.pages(config(3, a, b))
        assertEquals(listOf("a", "b", "b"), pages.map { it.view.id })
        // View b's auto tiles take every camera not fixed in b itself, so c2 shows up there too.
        assertEquals(listOf(listOf("c2"), listOf("c1", "c2"), listOf("c3", "-")), pageIds(config(3, a, b)))
    }

    @Test
    fun locate_prefersTheGivenPage() {
        val a = CamView("a", "", 1, 1, listOf(Tile(0, 0, camera = "c2")))
        val b = CamView.uniform("b", "", 2, 1)
        val pages = ViewPaging.pages(config(3, a, b))
        assertEquals(GridPosition(0, 0), ViewPaging.locate(pages, "c2", preferPage = 0))
        assertEquals(GridPosition(1, 1), ViewPaging.locate(pages, "c2", preferPage = 1))
        assertEquals(GridPosition(0, 0), ViewPaging.locate(pages, "c2", preferPage = 2))
        assertEquals(GridPosition(2, 0), ViewPaging.locate(pages, "c3", preferPage = 0))
        assertNull(ViewPaging.locate(pages, "nope", preferPage = 0))
    }
}
