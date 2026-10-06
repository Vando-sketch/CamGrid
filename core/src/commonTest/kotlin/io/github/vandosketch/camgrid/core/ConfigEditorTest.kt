package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertTrue

class ConfigEditorTest {

    private fun cam(id: String, name: String = "Camera $id") =
        Camera(id = id, name = name, gridUrl = "rtsp://192.0.2.10:554/$id")

    private fun config(vararg ids: String) =
        CamGridConfig(cameras = ids.map { cam(it) }, go2rtcBaseUrl = "http://192.0.2.10:1984")

    private fun ids(config: CamGridConfig) = config.cameras.map { it.id }

    // addCamera

    @Test
    fun addCamera_appendsAtEnd() {
        val result = ConfigEditor.addCamera(config("a", "b"), cam("c"))
        assertEquals(listOf("a", "b", "c"), ids(result))
        assertEquals(cam("c"), result.cameras.last())
    }

    @Test
    fun addCamera_toEmptyConfig() {
        val result = ConfigEditor.addCamera(CamGridConfig(), cam("a"))
        assertEquals(listOf(cam("a")), result.cameras)
    }

    @Test
    fun addCamera_duplicateIdThrows() {
        assertFailsWith<IllegalArgumentException> {
            ConfigEditor.addCamera(config("a", "b"), cam("b", name = "Other"))
        }
    }

    @Test
    fun addCamera_leavesReceiverUntouched() {
        val original = config("a", "b")
        val snapshot = original.copy()
        ConfigEditor.addCamera(original, cam("c"))
        assertEquals(snapshot, original)
        assertEquals(listOf("a", "b"), ids(original))
    }

    @Test
    fun addCamera_keepsOtherFields() {
        val original = config("a").copy(views = listOf(CamView.uniform("v", "V", 3, 2)))
        val result = ConfigEditor.addCamera(original, cam("b"))
        assertEquals(original.views, result.views)
        assertEquals(original.go2rtcBaseUrl, result.go2rtcBaseUrl)
        assertEquals(original.version, result.version)
    }

    // updateCamera

    @Test
    fun updateCamera_replacesKeepingPosition() {
        val updated = cam("b", name = "Renamed").copy(detailUrl = "rtsp://192.0.2.10:554/b_main")
        val result = ConfigEditor.updateCamera(config("a", "b", "c"), updated)
        assertEquals(listOf("a", "b", "c"), ids(result))
        assertEquals(updated, result.cameras[1])
        assertEquals(cam("a"), result.cameras[0])
        assertEquals(cam("c"), result.cameras[2])
    }

    @Test
    fun updateCamera_unknownIdThrows() {
        assertFailsWith<IllegalArgumentException> {
            ConfigEditor.updateCamera(config("a", "b"), cam("x"))
        }
    }

    @Test
    fun updateCamera_unknownIdOnEmptyConfigThrows() {
        assertFailsWith<IllegalArgumentException> {
            ConfigEditor.updateCamera(CamGridConfig(), cam("x"))
        }
    }

    @Test
    fun updateCamera_leavesReceiverUntouched() {
        val original = config("a", "b")
        val snapshot = original.copy()
        ConfigEditor.updateCamera(original, cam("a", name = "Renamed"))
        assertEquals(snapshot, original)
        assertEquals("Camera a", original.cameras[0].name)
    }

    // removeCamera

    @Test
    fun removeCamera_removesById() {
        val result = ConfigEditor.removeCamera(config("a", "b", "c"), "b")
        assertEquals(listOf("a", "c"), ids(result))
    }

    @Test
    fun removeCamera_lastRemainingLeavesEmpty() {
        val result = ConfigEditor.removeCamera(config("a"), "a")
        assertEquals(emptyList<Camera>(), result.cameras)
    }

    @Test
    fun removeCamera_unknownIdReturnsUnchanged() {
        val original = config("a", "b")
        assertEquals(original, ConfigEditor.removeCamera(original, "x"))
    }

    @Test
    fun removeCamera_leavesReceiverUntouched() {
        val original = config("a", "b", "c")
        val snapshot = original.copy()
        ConfigEditor.removeCamera(original, "a")
        assertEquals(snapshot, original)
        assertEquals(listOf("a", "b", "c"), ids(original))
    }

    // moveCamera

    @Test
    fun moveCamera_forward() {
        val result = ConfigEditor.moveCamera(config("a", "b", "c", "d"), "a", 2)
        assertEquals(listOf("b", "c", "a", "d"), ids(result))
    }

    @Test
    fun moveCamera_backward() {
        val result = ConfigEditor.moveCamera(config("a", "b", "c", "d"), "d", 1)
        assertEquals(listOf("a", "d", "b", "c"), ids(result))
    }

    @Test
    fun moveCamera_toSameIndexKeepsOrder() {
        val result = ConfigEditor.moveCamera(config("a", "b", "c"), "b", 1)
        assertEquals(listOf("a", "b", "c"), ids(result))
    }

    @Test
    fun moveCamera_toLastIndex() {
        val result = ConfigEditor.moveCamera(config("a", "b", "c"), "a", 2)
        assertEquals(listOf("b", "c", "a"), ids(result))
    }

    @Test
    fun moveCamera_negativeIndexClampedToFirst() {
        val result = ConfigEditor.moveCamera(config("a", "b", "c"), "c", -5)
        assertEquals(listOf("c", "a", "b"), ids(result))
    }

    @Test
    fun moveCamera_tooLargeIndexClampedToLast() {
        val result = ConfigEditor.moveCamera(config("a", "b", "c"), "a", 99)
        assertEquals(listOf("b", "c", "a"), ids(result))
    }

    @Test
    fun moveCamera_indexEqualToSizeClampedToLast() {
        val result = ConfigEditor.moveCamera(config("a", "b", "c"), "b", 3)
        assertEquals(listOf("a", "c", "b"), ids(result))
    }

    @Test
    fun moveCamera_unknownIdReturnsUnchanged() {
        val original = config("a", "b", "c")
        assertEquals(original, ConfigEditor.moveCamera(original, "x", 0))
    }

    @Test
    fun moveCamera_keepsCameraData() {
        val result = ConfigEditor.moveCamera(config("a", "b"), "b", 0)
        assertEquals(listOf(cam("b"), cam("a")), result.cameras)
    }

    @Test
    fun moveCamera_leavesReceiverUntouched() {
        val original = config("a", "b", "c")
        val snapshot = original.copy()
        ConfigEditor.moveCamera(original, "a", 2)
        assertEquals(snapshot, original)
        assertEquals(listOf("a", "b", "c"), ids(original))
    }

    // importCameras

    @Test
    fun importCameras_addsNewCamerasAtEndInOrder() {
        val result = ConfigEditor.importCameras(config("a"), listOf(cam("c"), cam("b")))
        assertEquals(listOf("a", "c", "b"), ids(result))
    }

    @Test
    fun importCameras_skipsExistingIdsAndKeepsExistingCameras() {
        val existing = cam("a", name = "Original")
        val start = CamGridConfig(cameras = listOf(existing, cam("b")))
        val result = ConfigEditor.importCameras(
            start,
            listOf(cam("b", name = "Imported b"), cam("c"), cam("a", name = "Imported a")),
        )
        assertEquals(listOf("a", "b", "c"), ids(result))
        assertEquals(existing, result.cameras[0])
        assertEquals("Camera b", result.cameras[1].name)
        assertEquals(cam("c"), result.cameras[2])
    }

    @Test
    fun importCameras_emptyListReturnsEqualConfig() {
        val original = config("a", "b")
        assertEquals(original, ConfigEditor.importCameras(original, emptyList()))
    }

    @Test
    fun importCameras_allExistingReturnsEqualConfig() {
        val original = config("a", "b")
        assertEquals(original, ConfigEditor.importCameras(original, listOf(cam("b", "X"), cam("a", "Y"))))
    }

    @Test
    fun importCameras_intoEmptyConfig() {
        val result = ConfigEditor.importCameras(CamGridConfig(), listOf(cam("x"), cam("y")))
        assertEquals(listOf(cam("x"), cam("y")), result.cameras)
    }

    @Test
    fun importCameras_leavesReceiverUntouched() {
        val original = config("a")
        val snapshot = original.copy()
        ConfigEditor.importCameras(original, listOf(cam("b")))
        assertEquals(snapshot, original)
        assertEquals(listOf("a"), ids(original))
    }

    // removeCamera and views

    @Test
    fun removeCamera_turnsTilesShowingItIntoAutoTiles() {
        val view = CamView(
            id = "v", columns = 2, rows = 1,
            tiles = listOf(Tile(0, 0, camera = "a"), Tile(1, 0, camera = "b")),
        )
        val result = ConfigEditor.removeCamera(config("a", "b").copy(views = listOf(view)), "a")
        assertEquals(listOf(null, "b"), result.views.single().tiles.map { it.camera })
    }

    // views

    private fun view(id: String) = CamView.uniform(id, "View $id", 2, 2)

    private fun viewIds(config: CamGridConfig) = config.views.map { it.id }

    @Test
    fun addView_appendsAtEnd() {
        val result = ConfigEditor.addView(config(), view("second"))
        assertEquals(listOf(CamGridConfig.DEFAULT_VIEW_ID, "second"), viewIds(result))
    }

    @Test
    fun addView_duplicateIdThrows() {
        assertFailsWith<IllegalArgumentException> {
            ConfigEditor.addView(config(), view(CamGridConfig.DEFAULT_VIEW_ID))
        }
    }

    @Test
    fun updateView_replacesKeepingPosition() {
        val start = ConfigEditor.addView(config(), view("second"))
        val changed = CamView.uniform(CamGridConfig.DEFAULT_VIEW_ID, "Renamed", 3, 1)
        val result = ConfigEditor.updateView(start, changed)
        assertEquals(listOf(changed, view("second")), result.views)
    }

    @Test
    fun updateView_unknownIdThrows() {
        assertFailsWith<IllegalArgumentException> { ConfigEditor.updateView(config(), view("x")) }
    }

    @Test
    fun removeView_removesById() {
        val start = ConfigEditor.addView(config(), view("second"))
        assertEquals(listOf("second"), viewIds(ConfigEditor.removeView(start, CamGridConfig.DEFAULT_VIEW_ID)))
    }

    @Test
    fun removeView_keepsTheLastView() {
        val start = config()
        assertEquals(start, ConfigEditor.removeView(start, CamGridConfig.DEFAULT_VIEW_ID))
    }

    @Test
    fun moveView_clampsTarget() {
        val start = ConfigEditor.addView(ConfigEditor.addView(config(), view("b")), view("c"))
        assertEquals(listOf("b", "c", CamGridConfig.DEFAULT_VIEW_ID), viewIds(ConfigEditor.moveView(start, CamGridConfig.DEFAULT_VIEW_ID, 99)))
        assertEquals(listOf("c", CamGridConfig.DEFAULT_VIEW_ID, "b"), viewIds(ConfigEditor.moveView(start, "c", -5)))
    }

    @Test
    fun newViewId_isUnused() {
        val start = ConfigEditor.addView(config(), view("view-2"))
        val id = ConfigEditor.newViewId(start)
        assertTrue(id !in viewIds(start), id)
        assertEquals("view-3", id)
    }
}
