package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Go2rtcTest {

    private val base = "http://192.0.2.10:1984"
    private val rtsp = "rtsp://192.0.2.10:8554/"

    // Constants

    @Test
    fun constants() {
        assertEquals(8554, Go2rtc.DEFAULT_RTSP_PORT)
        assertEquals(setOf("medium", "med", "low", "sub", "sd", "lq", "small", "grid"), Go2rtc.GRID_SUFFIXES)
        assertEquals(setOf("high", "main", "hd", "hq", "full", "detail"), Go2rtc.DETAIL_SUFFIXES)
    }

    // streamsApiUrl

    @Test
    fun streamsApiUrl_appendsPath() {
        assertEquals("http://192.0.2.10:1984/api/streams", Go2rtc.streamsApiUrl("http://192.0.2.10:1984"))
    }

    @Test
    fun streamsApiUrl_toleratesTrailingSlash() {
        assertEquals("http://192.0.2.10:1984/api/streams", Go2rtc.streamsApiUrl("http://192.0.2.10:1984/"))
    }

    @Test
    fun streamsApiUrl_addsHttpWhenSchemeMissing() {
        assertEquals("http://192.0.2.10:1984/api/streams", Go2rtc.streamsApiUrl("192.0.2.10:1984"))
        assertEquals("http://cam.example.com/api/streams", Go2rtc.streamsApiUrl("cam.example.com"))
    }

    @Test
    fun streamsApiUrl_missingSchemeAndTrailingSlash() {
        assertEquals("http://192.0.2.10:1984/api/streams", Go2rtc.streamsApiUrl("192.0.2.10:1984/"))
    }

    @Test
    fun streamsApiUrl_keepsHttps() {
        assertEquals("https://cam.example.com/api/streams", Go2rtc.streamsApiUrl("https://cam.example.com"))
        assertEquals("https://cam.example.com:1984/api/streams", Go2rtc.streamsApiUrl("https://cam.example.com:1984/"))
    }

    // parseStreamNames

    @Test
    fun parseStreamNames_inJsonOrder() {
        val json = """
            {
              "zeta_sub": {"producers": [{"url": "rtsp://192.0.2.20/sub"}], "consumers": null},
              "alpha": {"producers": [], "consumers": []},
              "middle_main": {}
            }
        """.trimIndent()
        assertEquals(listOf("zeta_sub", "alpha", "middle_main"), Go2rtc.parseStreamNames(json))
    }

    @Test
    fun parseStreamNames_valuesOfAnyShape() {
        val json = """{"a": null, "b": 1, "c": "x", "d": [1, 2], "e": {"nested": {"deep": true}}}"""
        assertEquals(listOf("a", "b", "c", "d", "e"), Go2rtc.parseStreamNames(json))
    }

    @Test
    fun parseStreamNames_emptyObject() {
        assertEquals(emptyList<String>(), Go2rtc.parseStreamNames("{}"))
    }

    @Test
    fun parseStreamNames_namesWithSpaces() {
        assertEquals(listOf("front door", "back yard_sub"), Go2rtc.parseStreamNames("""{"front door": {}, "back yard_sub": {}}"""))
    }

    @Test
    fun parseStreamNames_rejectsArray() {
        assertThrows(IllegalArgumentException::class.java) { Go2rtc.parseStreamNames("""["front", "back"]""") }
    }

    @Test
    fun parseStreamNames_rejectsPrimitives() {
        assertThrows(IllegalArgumentException::class.java) { Go2rtc.parseStreamNames("\"front\"") }
        assertThrows(IllegalArgumentException::class.java) { Go2rtc.parseStreamNames("42") }
        assertThrows(IllegalArgumentException::class.java) { Go2rtc.parseStreamNames("null") }
        assertThrows(IllegalArgumentException::class.java) { Go2rtc.parseStreamNames("true") }
    }

    @Test
    fun parseStreamNames_rejectsMalformed() {
        assertThrows(IllegalArgumentException::class.java) { Go2rtc.parseStreamNames("") }
        assertThrows(IllegalArgumentException::class.java) { Go2rtc.parseStreamNames("{") }
        assertThrows(IllegalArgumentException::class.java) { Go2rtc.parseStreamNames("<html>502 Bad Gateway</html>") }
    }

    // rtspUrl

    @Test
    fun rtspUrl_sameHostDefaultPort() {
        assertEquals("rtsp://192.0.2.10:8554/front", Go2rtc.rtspUrl("http://192.0.2.10:1984", "front"))
    }

    @Test
    fun rtspUrl_customPort() {
        assertEquals("rtsp://192.0.2.10:9554/front", Go2rtc.rtspUrl("http://192.0.2.10:1984", "front", rtspPort = 9554))
    }

    @Test
    fun rtspUrl_baseWithoutPort() {
        assertEquals("rtsp://cam.example.com:8554/front", Go2rtc.rtspUrl("http://cam.example.com", "front"))
        assertEquals("rtsp://cam.example.com:8554/front", Go2rtc.rtspUrl("https://cam.example.com", "front"))
    }

    @Test
    fun rtspUrl_baseWithTrailingSlash() {
        assertEquals("rtsp://192.0.2.10:8554/front", Go2rtc.rtspUrl("http://192.0.2.10:1984/", "front"))
    }

    @Test
    fun rtspUrl_carriesUserInfo() {
        assertEquals(
            "rtsp://viewer:secret@192.0.2.10:8554/front",
            Go2rtc.rtspUrl("http://viewer:secret@192.0.2.10:1984", "front"),
        )
    }

    @Test
    fun rtspUrl_carriesUserInfoWithoutPassword() {
        assertEquals(
            "rtsp://viewer@198.51.100.5:8554/front",
            Go2rtc.rtspUrl("http://viewer@198.51.100.5:1984", "front"),
        )
    }

    @Test
    fun rtspUrl_percentEncodesSpaces() {
        assertEquals("rtsp://192.0.2.10:8554/front%20door", Go2rtc.rtspUrl(base, "front door"))
        assertEquals("rtsp://192.0.2.10:8554/back%20yard_sub", Go2rtc.rtspUrl(base, "back yard_sub"))
    }

    @Test
    fun rtspUrl_leavesUnreservedCharacters() {
        assertEquals("rtsp://192.0.2.10:8554/front_sub", Go2rtc.rtspUrl(base, "front_sub"))
        assertEquals("rtsp://192.0.2.10:8554/front-door.hd", Go2rtc.rtspUrl(base, "front-door.hd"))
        assertEquals("rtsp://192.0.2.10:8554/Cam2", Go2rtc.rtspUrl(base, "Cam2"))
    }

    // suggestCameras

    @Test
    fun suggest_emptyList() {
        assertEquals(emptyList<Camera>(), Go2rtc.suggestCameras(base, emptyList()))
    }

    @Test
    fun suggest_gridAndDetailVariants() {
        assertEquals(
            listOf(Camera("go2rtc:front", "front", rtsp + "front_sub", rtsp + "front_main")),
            Go2rtc.suggestCameras(base, listOf("front_sub", "front_main")),
        )
    }

    @Test
    fun suggest_detailBeforeGridInList() {
        assertEquals(
            listOf(Camera("go2rtc:front", "front", rtsp + "front_low", rtsp + "front_hd")),
            Go2rtc.suggestCameras(base, listOf("front_hd", "front_low")),
        )
    }

    @Test
    fun suggest_gridAndDetailIgnorePlainBase() {
        assertEquals(
            listOf(Camera("go2rtc:front", "front", rtsp + "front_sub", rtsp + "front_main")),
            Go2rtc.suggestCameras(base, listOf("front", "front_sub", "front_main")),
        )
    }

    @Test
    fun suggest_onlyPlainBase() {
        assertEquals(
            listOf(Camera("go2rtc:garage", "garage", rtsp + "garage", "")),
            Go2rtc.suggestCameras(base, listOf("garage")),
        )
    }

    @Test
    fun suggest_plainBaseAndDetail() {
        assertEquals(
            listOf(Camera("go2rtc:porch", "porch", rtsp + "porch", rtsp + "porch_hd")),
            Go2rtc.suggestCameras(base, listOf("porch", "porch_hd")),
        )
    }

    @Test
    fun suggest_plainBaseAndGrid() {
        assertEquals(
            listOf(Camera("go2rtc:yard", "yard", rtsp + "yard_low", rtsp + "yard")),
            Go2rtc.suggestCameras(base, listOf("yard", "yard_low")),
        )
    }

    @Test
    fun suggest_onlyGridVariant() {
        assertEquals(
            listOf(Camera("go2rtc:side", "side", rtsp + "side_sub", "")),
            Go2rtc.suggestCameras(base, listOf("side_sub")),
        )
    }

    @Test
    fun suggest_onlyDetailVariantUsedForGrid() {
        val cameras = Go2rtc.suggestCameras(base, listOf("drive_main"))
        assertEquals(1, cameras.size)
        val camera = cameras.single()
        assertEquals("go2rtc:drive", camera.id)
        assertEquals("drive", camera.name)
        assertEquals(rtsp + "drive_main", camera.gridUrl)
        assertEquals(rtsp + "drive_main", camera.fullscreenUrl)
    }

    @Test
    fun suggest_onlyDetailVariantHasBlankDetailUrl() {
        assertEquals(
            listOf(Camera("go2rtc:drive", "drive", rtsp + "drive_main", "")),
            Go2rtc.suggestCameras(base, listOf("drive_main")),
        )
    }

    @Test
    fun suggest_splitLeavingEmptyBaseIsOwnBase() {
        assertEquals(
            listOf(
                Camera("go2rtc:_sub", "_sub", rtsp + "_sub", ""),
                Camera("go2rtc:-main", "-main", rtsp + "-main", ""),
                Camera("go2rtc:.hd", ".hd", rtsp + ".hd", ""),
            ),
            Go2rtc.suggestCameras(base, listOf("_sub", "-main", ".hd")),
        )
    }

    @Test
    fun suggest_baseNamesAreCaseSensitive() {
        assertEquals(
            listOf(
                Camera("go2rtc:Front", "Front", rtsp + "Front_sub", ""),
                Camera("go2rtc:front", "front", rtsp + "front_main", ""),
            ),
            Go2rtc.suggestCameras(base, listOf("Front_sub", "front_main")),
        )
    }

    @Test
    fun suggest_firstGridAndDetailVariantWin() {
        assertEquals(
            listOf(Camera("go2rtc:cam", "cam", rtsp + "cam_low", rtsp + "cam_hd")),
            Go2rtc.suggestCameras(base, listOf("cam_hd", "cam_low", "cam_sub", "cam_main", "cam_medium", "cam")),
        )
    }

    @Test
    fun suggest_unrelatedNamesAreOwnCameras() {
        assertEquals(
            listOf(
                Camera("go2rtc:kitchen", "kitchen", rtsp + "kitchen", ""),
                Camera("go2rtc:backyard_cam", "backyard_cam", rtsp + "backyard_cam", ""),
                Camera("go2rtc:front-door", "front-door", rtsp + "front-door", ""),
            ),
            Go2rtc.suggestCameras(base, listOf("kitchen", "backyard_cam", "front-door")),
        )
    }

    @Test
    fun suggest_suffixWithoutSeparatorIsNotAVariant() {
        assertEquals(
            listOf(
                Camera("go2rtc:frontsub", "frontsub", rtsp + "frontsub", ""),
                Camera("go2rtc:hd", "hd", rtsp + "hd", ""),
            ),
            Go2rtc.suggestCameras(base, listOf("frontsub", "hd")),
        )
    }

    @Test
    fun suggest_allSeparators() {
        assertEquals(
            listOf(
                Camera("go2rtc:a", "a", rtsp + "a_sub", rtsp + "a-main"),
                Camera("go2rtc:b", "b", rtsp + "b-low", rtsp + "b.hd"),
                Camera("go2rtc:c", "c", rtsp + "c.sd", rtsp + "c_hq"),
            ),
            Go2rtc.suggestCameras(base, listOf("a_sub", "a-main", "b-low", "b.hd", "c.sd", "c_hq")),
        )
    }

    @Test
    fun suggest_splitsAtLastSeparator() {
        assertEquals(
            listOf(Camera("go2rtc:front_door", "front_door", rtsp + "front_door_sub", rtsp + "front_door-main")),
            Go2rtc.suggestCameras(base, listOf("front_door_sub", "front_door-main")),
        )
        assertEquals(
            listOf(Camera("go2rtc:cam.1-east", "cam.1-east", rtsp + "cam.1-east.low", "")),
            Go2rtc.suggestCameras(base, listOf("cam.1-east.low")),
        )
    }

    @Test
    fun suggest_everyGridSuffixRecognised() {
        for (suffix in Go2rtc.GRID_SUFFIXES) {
            assertEquals(
                suffix,
                listOf(Camera("go2rtc:cam", "cam", rtsp + "cam_$suffix", rtsp + "cam")),
                Go2rtc.suggestCameras(base, listOf("cam", "cam_$suffix")),
            )
        }
    }

    @Test
    fun suggest_everyDetailSuffixRecognised() {
        for (suffix in Go2rtc.DETAIL_SUFFIXES) {
            assertEquals(
                suffix,
                listOf(Camera("go2rtc:cam", "cam", rtsp + "cam", rtsp + "cam_$suffix")),
                Go2rtc.suggestCameras(base, listOf("cam", "cam_$suffix")),
            )
        }
    }

    @Test
    fun suggest_suffixesCaseInsensitive() {
        assertEquals(
            listOf(Camera("go2rtc:Front", "Front", rtsp + "Front_SUB", rtsp + "Front_Main")),
            Go2rtc.suggestCameras(base, listOf("Front_SUB", "Front_Main")),
        )
        assertEquals(
            listOf(Camera("go2rtc:gate", "gate", rtsp + "gate-Low", rtsp + "gate.HD")),
            Go2rtc.suggestCameras(base, listOf("gate.HD", "gate-Low")),
        )
    }

    @Test
    fun suggest_orderedByFirstAppearanceOfBase() {
        val cameras = Go2rtc.suggestCameras(
            base,
            listOf("b_sub", "a", "c_main", "b_main", "a_hd", "c_low"),
        )
        assertEquals(listOf("go2rtc:b", "go2rtc:a", "go2rtc:c"), cameras.map { it.id })
        assertEquals(listOf("b", "a", "c"), cameras.map { it.name })
        assertEquals(
            listOf(
                Camera("go2rtc:b", "b", rtsp + "b_sub", rtsp + "b_main"),
                Camera("go2rtc:a", "a", rtsp + "a", rtsp + "a_hd"),
                Camera("go2rtc:c", "c", rtsp + "c_low", rtsp + "c_main"),
            ),
            cameras,
        )
    }

    @Test
    fun suggest_mixedWall() {
        val cameras = Go2rtc.suggestCameras(
            base,
            listOf("front_main", "garage", "front_sub", "yard", "yard_low", "porch_hd"),
        )
        assertEquals(listOf("go2rtc:front", "go2rtc:garage", "go2rtc:yard", "go2rtc:porch"), cameras.map { it.id })
        assertEquals(Camera("go2rtc:front", "front", rtsp + "front_sub", rtsp + "front_main"), cameras[0])
        assertEquals(Camera("go2rtc:garage", "garage", rtsp + "garage", ""), cameras[1])
        assertEquals(Camera("go2rtc:yard", "yard", rtsp + "yard_low", rtsp + "yard"), cameras[2])
        assertEquals(rtsp + "porch_hd", cameras[3].gridUrl)
        assertEquals(rtsp + "porch_hd", cameras[3].fullscreenUrl)
    }

    @Test
    fun suggest_usesRtspPortAndUserInfo() {
        val cameras = Go2rtc.suggestCameras(
            "http://viewer:secret@192.0.2.10:1984",
            listOf("front_sub", "front_main"),
            rtspPort = 9554,
        )
        assertEquals(
            listOf(
                Camera(
                    "go2rtc:front",
                    "front",
                    "rtsp://viewer:secret@192.0.2.10:9554/front_sub",
                    "rtsp://viewer:secret@192.0.2.10:9554/front_main",
                ),
            ),
            cameras,
        )
    }

    @Test
    fun suggest_encodesSpacesInUrlsButNotInNameOrId() {
        val cameras = Go2rtc.suggestCameras(base, listOf("front door_sub", "front door_main"))
        assertEquals(
            listOf(Camera("go2rtc:front door", "front door", rtsp + "front%20door_sub", rtsp + "front%20door_main")),
            cameras,
        )
    }

    @Test
    fun suggest_urlsMatchRtspUrl() {
        val cameras = Go2rtc.suggestCameras(base, listOf("x_sub", "x_hd"))
        assertTrue(cameras.isNotEmpty())
        assertEquals(Go2rtc.rtspUrl(base, "x_sub"), cameras[0].gridUrl)
        assertEquals(Go2rtc.rtspUrl(base, "x_hd"), cameras[0].detailUrl)
    }
}
