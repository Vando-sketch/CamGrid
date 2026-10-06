package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridPagingTest {

    private fun cam(id: String) = Camera(id = id, name = id, gridUrl = "rtsp://192.0.2.10:554/$id")

    private fun config(count: Int, columns: Int = 2, rows: Int = 2) =
        CamGridConfig(layout = GridLayout(columns, rows), cameras = (1..count).map { cam("c$it") })

    private fun pageIds(config: CamGridConfig) = GridPaging.pages(config).map { page -> page.map { it.id } }

    @Test
    fun pages_emptyConfigIsEmptyList() {
        assertEquals(emptyList<List<Camera>>(), GridPaging.pages(CamGridConfig()))
    }

    @Test
    fun pageCount_emptyConfigIsZero() {
        assertEquals(0, GridPaging.pageCount(CamGridConfig()))
    }

    @Test
    fun pages_fewerThanOnePage() {
        assertEquals(listOf(listOf("c1", "c2", "c3")), pageIds(config(3)))
    }

    @Test
    fun pages_exactlyFullPages() {
        assertEquals(
            listOf(listOf("c1", "c2", "c3", "c4"), listOf("c5", "c6", "c7", "c8")),
            pageIds(config(8)),
        )
    }

    @Test
    fun pages_partialLastPage() {
        assertEquals(
            listOf(listOf("c1", "c2", "c3", "c4"), listOf("c5")),
            pageIds(config(5)),
        )
    }

    @Test
    fun pages_followConfigOrder() {
        val config = CamGridConfig(
            layout = GridLayout(1, 2),
            cameras = listOf(cam("z"), cam("a"), cam("m")),
        )
        assertEquals(listOf(listOf("z", "a"), listOf("m")), pageIds(config))
    }

    @Test
    fun pages_useTilesPerPageOfLayout() {
        assertEquals(listOf(6, 6, 6, 2), GridPaging.pages(config(20, columns = 3, rows = 2)).map { it.size })
        assertEquals(listOf(4, 4, 4, 4, 4), GridPaging.pages(config(20, columns = 4, rows = 1)).map { it.size })
        assertEquals(listOf(16, 4), GridPaging.pages(config(20, columns = 4, rows = 4)).map { it.size })
        assertEquals(listOf(1, 1, 1), GridPaging.pages(config(3, columns = 1, rows = 1)).map { it.size })
    }

    @Test
    fun pageCount_matchesPages() {
        assertEquals(1, GridPaging.pageCount(config(1)))
        assertEquals(1, GridPaging.pageCount(config(4)))
        assertEquals(2, GridPaging.pageCount(config(5)))
        assertEquals(2, GridPaging.pageCount(config(8)))
        assertEquals(3, GridPaging.pageCount(config(9)))
        assertEquals(20, GridPaging.pageCount(config(20, columns = 1, rows = 1)))
    }

    @Test
    fun pageOf_findsPage() {
        val config = config(9)
        assertEquals(0, GridPaging.pageOf(config, "c1"))
        assertEquals(0, GridPaging.pageOf(config, "c4"))
        assertEquals(1, GridPaging.pageOf(config, "c5"))
        assertEquals(1, GridPaging.pageOf(config, "c8"))
        assertEquals(2, GridPaging.pageOf(config, "c9"))
    }

    @Test
    fun pageOf_unknownIdIsNull() {
        assertNull(GridPaging.pageOf(config(5), "nope"))
    }

    @Test
    fun pageOf_emptyConfigIsNull() {
        assertNull(GridPaging.pageOf(CamGridConfig(), "c1"))
    }
}
