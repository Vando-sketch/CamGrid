package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigCodecTest {

    private val fullConfig = CamGridConfig(
        layout = GridLayout(columns = 3, rows = 2),
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
    fun roundTripBoundaryLayouts() {
        val small = CamGridConfig(layout = GridLayout(1, 1))
        val large = CamGridConfig(layout = GridLayout(4, 4))
        assertEquals(small, ConfigCodec.decode(ConfigCodec.encode(small)))
        assertEquals(large, ConfigCodec.decode(ConfigCodec.encode(large)))
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
    fun decodeExplicitJson() {
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
            layout = GridLayout(4, 3),
            cameras = listOf(Camera("a", "A", "rtsp://192.0.2.1/sub", "rtsp://192.0.2.1/main")),
            go2rtcBaseUrl = "http://192.0.2.10:1984",
            version = 1,
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
            layout = GridLayout(3, 1),
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
        assertEquals(GridLayout(), decoded.layout)
        assertEquals("", decoded.go2rtcBaseUrl)
        assertEquals(CamGridConfig.CURRENT_VERSION, decoded.version)
        assertEquals(listOf(Camera("a", "A", "rtsp://192.0.2.1/sub", "")), decoded.cameras)
    }

    @Test
    fun decodeMissingLayoutFieldTakesDefault() {
        assertEquals(GridLayout(columns = 3, rows = 2), ConfigCodec.decode("""{"layout":{"columns":3}}""").layout)
        assertEquals(GridLayout(columns = 2, rows = 4), ConfigCodec.decode("""{"layout":{"rows":4}}""").layout)
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
