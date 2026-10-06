package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigCodecTest {

    private val fullConfig = CamGridConfig(
        views = listOf(
            CamView.uniform("main", "Main", 3, 2),
            CamView(
                id = "kitchen",
                name = "Kitchen wall",
                columns = 4,
                rows = 2,
                tiles = listOf(
                    Tile(0, 0, 1, 2, camera = "go2rtc:front", fit = FitMode.CROP),
                    Tile(1, 0, 1, 2, camera = "garage", fit = FitMode.CROP),
                    Tile(2, 0, 2, 1),
                    Tile(2, 1, 2, 1, camera = "yard"),
                ),
            ),
        ),
        cameras = listOf(
            Camera(
                id = "go2rtc:front",
                name = "Front door",
                gridUrl = "rtsp://viewer:secret@192.0.2.10:8554/front_sub",
                detailUrl = "rtsp://viewer:secret@192.0.2.10:8554/front_main",
            ),
            Camera(id = "garage", name = "Garage \"east\" / ü", gridUrl = "https://cam.example.com/live.m3u8?token=secret"),
            Camera(id = "yard", name = "Yard", gridUrl = "rtsp://198.51.100.7/stream", detailUrl = ""),
        ),
        go2rtcBaseUrl = "http://192.0.2.10:1984",
        version = CamGridConfig.CURRENT_VERSION,
    )

    // encode / round trip

    @Test
    fun roundTripFullConfig() {
        assertEquals(fullConfig, ConfigCodec.decode(ConfigCodec.encode(fullConfig)))
    }

    @Test
    fun roundTripDefaultConfig() {
        assertEquals(CamGridConfig(), ConfigCodec.decode(ConfigCodec.encode(CamGridConfig())))
    }

    @Test
    fun roundTripBoundaryViews() {
        val small = CamGridConfig(views = listOf(CamView.uniform("v", "", 1, 1)))
        val large = CamGridConfig(views = listOf(CamView.uniform("v", "", 4, 4)))
        assertEquals(small, ConfigCodec.decode(ConfigCodec.encode(small)))
        assertEquals(large, ConfigCodec.decode(ConfigCodec.encode(large)))
    }

    @Test
    fun encodedViewsReferenceCamerasOnlyById() {
        // Views are meant to be served from elsewhere later; they must never carry stream URLs.
        val views = Regex("\"views\"(.*?)\"cameras\"", RegexOption.DOT_MATCHES_ALL)
            .find(ConfigCodec.encode(fullConfig))!!.groupValues[1]
        assert("rtsp" !in views && "secret" !in views && "http" !in views) { views }
    }

    @Test
    fun roundTripOtherVersionNumber() {
        val config = fullConfig.copy(version = 7)
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
    }

    @Test
    fun roundTripPreservesCameraOrder() {
        val decoded = ConfigCodec.decode(ConfigCodec.encode(fullConfig))
        assertEquals(listOf("go2rtc:front", "garage", "yard"), decoded.cameras.map { it.id })
    }

    // decode

    @Test
    fun decodeVersion1MigratesLayoutToUniformView() {
        val json = """
            {
              "layout": {"columns": 4, "rows": 3},
              "cameras": [
                {"id": "a", "name": "A", "gridUrl": "rtsp://192.0.2.1/sub", "detailUrl": "rtsp://192.0.2.1/main"}
              ],
              "go2rtcBaseUrl": "http://192.0.2.10:1984",
              "version": 1
            }
        """.trimIndent()
        val expected = CamGridConfig(
            views = listOf(CamView.uniform(CamGridConfig.DEFAULT_VIEW_ID, "", 4, 3)),
            cameras = listOf(Camera("a", "A", "rtsp://192.0.2.1/sub", "rtsp://192.0.2.1/main")),
            go2rtcBaseUrl = "http://192.0.2.10:1984",
            version = CamGridConfig.CURRENT_VERSION,
        )
        assertEquals(expected, ConfigCodec.decode(json))
    }

    @Test
    fun decodeIgnoresUnknownTopLevelKeys() {
        val json = """{"layout":{"columns":2,"rows":2},"cameras":[],"theme":"dark","extra":{"nested":[1,2,3]}}"""
        assertEquals(CamGridConfig(), ConfigCodec.decode(json))
    }

    @Test
    fun decodeIgnoresUnknownNestedKeys() {
        val json = """
            {
              "layout": {"columns": 3, "rows": 1, "gap": 4},
              "cameras": [
                {"id": "a", "name": "A", "gridUrl": "rtsp://192.0.2.1/sub", "muted": true, "rotation": 90}
              ]
            }
        """.trimIndent()
        val expected = CamGridConfig(
            views = listOf(CamView.uniform(CamGridConfig.DEFAULT_VIEW_ID, "", 3, 1)),
            cameras = listOf(Camera("a", "A", "rtsp://192.0.2.1/sub")),
        )
        assertEquals(expected, ConfigCodec.decode(json))
    }

    @Test
    fun decodeEmptyObjectGivesDefaults() {
        assertEquals(CamGridConfig(), ConfigCodec.decode("{}"))
    }

    @Test
    fun decodeMissingKeysTakeDefaults() {
        val json = """{"cameras":[{"id":"a","name":"A","gridUrl":"rtsp://192.0.2.1/sub"}]}"""
        val decoded = ConfigCodec.decode(json)
        assertEquals(CamGridConfig().views, decoded.views)
        assertEquals("", decoded.go2rtcBaseUrl)
        assertEquals(CamGridConfig.CURRENT_VERSION, decoded.version)
        assertEquals(listOf(Camera("a", "A", "rtsp://192.0.2.1/sub", "")), decoded.cameras)
    }

    @Test
    fun decodeMissingLayoutFieldTakesDefault() {
        assertEquals(listOf(CamView.uniform("main", "", 3, 2)), ConfigCodec.decode("""{"layout":{"columns":3}}""").views)
        assertEquals(listOf(CamView.uniform("main", "", 2, 4)), ConfigCodec.decode("""{"layout":{"rows":4}}""").views)
    }

    @Test
    fun decodeMalformedJsonThrows() {
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode("{") }
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode("not json at all") }
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode("""{"cameras": [}""") }
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode("") }
    }

    @Test
    fun decodeInvalidLayoutThrows() {
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode("""{"layout":{"columns":0,"rows":2}}""") }
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode("""{"layout":{"columns":2,"rows":0}}""") }
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode("""{"layout":{"columns":5,"rows":2}}""") }
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode("""{"layout":{"columns":2,"rows":5}}""") }
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode("""{"layout":{"columns":-1,"rows":2}}""") }
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode("""{"layout":"wide"}""") }
    }

    @Test
    fun decodeVersion2IgnoresLegacyLayout() {
        val json = """{"version":2,"layout":{"columns":4,"rows":4},"views":[{"id":"v","columns":1,"rows":1,"tiles":[{"x":0,"y":0}]}]}"""
        assertEquals(listOf(CamView("v", "", 1, 1, listOf(Tile(0, 0)))), ConfigCodec.decode(json).views)
    }

    @Test
    fun decodeTileDefaults() {
        val json = """{"version":2,"views":[{"id":"v","columns":2,"rows":1,"tiles":[{"x":1,"y":0}]}]}"""
        val tile = ConfigCodec.decode(json).views.single().tiles.single()
        assertEquals(Tile(x = 1, y = 0, w = 1, h = 1, camera = null, fit = FitMode.FIT), tile)
    }

    @Test
    fun decodeUnknownFitModeFallsBackToFit() {
        val json = """{"version":2,"views":[{"id":"v","columns":1,"rows":1,"tiles":[{"x":0,"y":0,"fit":"STRETCH"}]}]}"""
        assertEquals(FitMode.FIT, ConfigCodec.decode(json).views.single().tiles.single().fit)
    }

    @Test
    fun decodeInvalidViewsThrow() {
        fun v2(views: String) = """{"version":2,"views":$views}"""
        // Overlapping tiles, a tile outside the canvas, an empty view list, duplicate ids.
        assertThrows(ConfigFormatException::class.java) {
            ConfigCodec.decode(v2("""[{"id":"v","columns":2,"rows":1,"tiles":[{"x":0,"y":0,"w":2},{"x":1,"y":0}]}]"""))
        }
        assertThrows(ConfigFormatException::class.java) {
            ConfigCodec.decode(v2("""[{"id":"v","columns":2,"rows":1,"tiles":[{"x":1,"y":0,"w":2}]}]"""))
        }
        assertThrows(ConfigFormatException::class.java) { ConfigCodec.decode(v2("[]")) }
        assertThrows(ConfigFormatException::class.java) {
            ConfigCodec.decode(v2("""[{"id":"v","columns":1,"rows":1,"tiles":[]},{"id":"v","columns":1,"rows":1,"tiles":[]}]"""))
        }
    }

    // decodeOrDefault

    @Test
    fun decodeOrDefaultNull() {
        assertEquals(CamGridConfig(), ConfigCodec.decodeOrDefault(null))
    }

    @Test
    fun decodeOrDefaultBlank() {
        assertEquals(CamGridConfig(), ConfigCodec.decodeOrDefault(""))
        assertEquals(CamGridConfig(), ConfigCodec.decodeOrDefault("   \n\t"))
    }

    @Test
    fun decodeOrDefaultGarbage() {
        assertEquals(CamGridConfig(), ConfigCodec.decodeOrDefault("garbage"))
        assertEquals(CamGridConfig(), ConfigCodec.decodeOrDefault("{\"layout\":"))
    }

    @Test
    fun decodeOrDefaultInvalidLayout() {
        assertEquals(CamGridConfig(), ConfigCodec.decodeOrDefault("""{"layout":{"columns":9,"rows":9}}"""))
    }

    @Test
    fun decodeOrDefaultValidInput() {
        assertEquals(fullConfig, ConfigCodec.decodeOrDefault(ConfigCodec.encode(fullConfig)))
    }
}
