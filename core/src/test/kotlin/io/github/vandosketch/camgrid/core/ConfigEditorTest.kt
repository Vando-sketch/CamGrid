package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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
        assertThrows(IllegalArgumentException::class.java) {
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
        val original = config("a").copy(layout = GridLayout(3, 2))
        val result = ConfigEditor.addCamera(original, cam("b"))
        assertEquals(GridLayout(3, 2), result.layout)
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
        assertThrows(IllegalArgumentException::class.java) {
            ConfigEditor.updateCamera(config("a", "b"), cam("x"))
        }
    }

    @Test
    fun updateCamera_unknownIdOnEmptyConfigThrows() {
        assertThrows(IllegalArgumentException::class.java) {
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

    // setLayout

    @Test
    fun setLayout_setsColumnsAndRows() {
        val result = ConfigEditor.setLayout(config("a", "b"), columns = 3, rows = 2)
        assertEquals(GridLayout(3, 2), result.layout)
        assertEquals(listOf("a", "b"), ids(result))
    }

    @Test
    fun setLayout_acceptsBounds() {
        assertEquals(GridLayout(1, 1), ConfigEditor.setLayout(config(), 1, 1).layout)
        assertEquals(GridLayout(4, 4), ConfigEditor.setLayout(config(), 4, 4).layout)
        assertEquals(GridLayout(1, 4), ConfigEditor.setLayout(config(), 1, 4).layout)
    }

    @Test
    fun setLayout_rejectsZero() {
        assertThrows(IllegalArgumentException::class.java) { ConfigEditor.setLayout(config(), 0, 2) }
        assertThrows(IllegalArgumentException::class.java) { ConfigEditor.setLayout(config(), 2, 0) }
    }

    @Test
    fun setLayout_rejectsFive() {
        assertThrows(IllegalArgumentException::class.java) { ConfigEditor.setLayout(config(), 5, 2) }
        assertThrows(IllegalArgumentException::class.java) { ConfigEditor.setLayout(config(), 2, 5) }
    }

    @Test
    fun setLayout_rejectsNegative() {
        assertThrows(IllegalArgumentException::class.java) { ConfigEditor.setLayout(config(), -1, 2) }
    }

    @Test
    fun setLayout_leavesReceiverUntouched() {
        val original = config("a")
        val snapshot = original.copy()
        ConfigEditor.setLayout(original, 4, 3)
        assertEquals(snapshot, original)
        assertEquals(GridLayout(), original.layout)
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
}
