package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class CameraValidatorTest {

    private fun camera(
        name: String = "Front door",
        gridUrl: String = "rtsp://192.0.2.10:8554/front_sub",
        detailUrl: String = "",
    ) = Camera(id = "c1", name = name, gridUrl = gridUrl, detailUrl = detailUrl)

    // isValidStreamUrl

    @Test
    fun supportedSchemes() {
        assertEquals(setOf("rtsp", "rtsps", "http", "https"), CameraValidator.SUPPORTED_SCHEMES)
    }

    @Test
    fun acceptsEverySupportedScheme() {
        assertTrue(CameraValidator.isValidStreamUrl("rtsp://192.0.2.10:554/stream"))
        assertTrue(CameraValidator.isValidStreamUrl("rtsps://192.0.2.10:322/stream"))
        assertTrue(CameraValidator.isValidStreamUrl("http://cam.example.com/video.m3u8"))
        assertTrue(CameraValidator.isValidStreamUrl("https://cam.example.com/video.m3u8"))
    }

    @Test
    fun schemeIsCaseInsensitive() {
        assertTrue(CameraValidator.isValidStreamUrl("RTSP://192.0.2.10/stream"))
        assertTrue(CameraValidator.isValidStreamUrl("Rtsps://192.0.2.10/stream"))
        assertTrue(CameraValidator.isValidStreamUrl("HTTPS://cam.example.com/live"))
        assertTrue(CameraValidator.isValidStreamUrl("hTtP://cam.example.com/live"))
    }

    @Test
    fun rejectsUnsupportedScheme() {
        assertFalse(CameraValidator.isValidStreamUrl("ftp://192.0.2.10/stream"))
        assertFalse(CameraValidator.isValidStreamUrl("rtmp://192.0.2.10/live"))
        assertFalse(CameraValidator.isValidStreamUrl("file:///sdcard/video.mp4"))
    }

    @Test
    fun rejectsMissingScheme() {
        assertFalse(CameraValidator.isValidStreamUrl("192.0.2.10:554/stream"))
        assertFalse(CameraValidator.isValidStreamUrl("cam.example.com/stream"))
        assertFalse(CameraValidator.isValidStreamUrl("//cam.example.com/stream"))
    }

    @Test
    fun acceptsHostOnly() {
        assertTrue(CameraValidator.isValidStreamUrl("rtsp://192.0.2.10"))
        assertTrue(CameraValidator.isValidStreamUrl("http://cam.example.com"))
    }

    @Test
    fun acceptsUserInfoPortPathAndQuery() {
        assertTrue(CameraValidator.isValidStreamUrl("rtsp://viewer:secret@192.0.2.10:554/Streaming/Channels/102"))
        assertTrue(CameraValidator.isValidStreamUrl("http://cam.example.com:8080/video?channel=1&subtype=1"))
    }

    @Test
    fun rejectsMissingHost() {
        assertFalse(CameraValidator.isValidStreamUrl("rtsp://"))
        assertFalse(CameraValidator.isValidStreamUrl("rtsp:///stream"))
        assertFalse(CameraValidator.isValidStreamUrl("http://:8080/stream"))
        assertFalse(CameraValidator.isValidStreamUrl("rtsp://viewer:secret@/stream"))
    }

    @Test
    fun rejectsInnerWhitespace() {
        assertFalse(CameraValidator.isValidStreamUrl("rtsp://192.0.2.10/front door"))
        assertFalse(CameraValidator.isValidStreamUrl("rtsp://192.0.2. 10/stream"))
        assertFalse(CameraValidator.isValidStreamUrl("rtsp://192.0.2.10/a\tb"))
        assertFalse(CameraValidator.isValidStreamUrl("rtsp ://192.0.2.10/stream"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertTrue(CameraValidator.isValidStreamUrl("  rtsp://192.0.2.10/stream  "))
        assertTrue(CameraValidator.isValidStreamUrl("\thttp://cam.example.com/live\n"))
    }

    @Test
    fun rejectsBlankAndNonUrl() {
        assertFalse(CameraValidator.isValidStreamUrl(""))
        assertFalse(CameraValidator.isValidStreamUrl("   "))
        assertFalse(CameraValidator.isValidStreamUrl("not a url"))
        assertFalse(CameraValidator.isValidStreamUrl("rtsp"))
    }

    // validate

    @Test
    fun validCameraHasNoErrors() {
        assertEquals(emptySet<CameraError>(), CameraValidator.validate(camera()))
    }

    @Test
    fun validCameraWithDetailUrlHasNoErrors() {
        val c = camera(detailUrl = "rtsp://viewer:secret@192.0.2.10:8554/front_main")
        assertEquals(emptySet<CameraError>(), CameraValidator.validate(c))
    }

    @Test
    fun blankDetailUrlIsAllowed() {
        assertEquals(emptySet<CameraError>(), CameraValidator.validate(camera(detailUrl = "")))
        assertEquals(emptySet<CameraError>(), CameraValidator.validate(camera(detailUrl = "   ")))
    }

    @Test
    fun urlsWithSurroundingWhitespaceAreValid() {
        val c = camera(gridUrl = " rtsp://192.0.2.10/sub ", detailUrl = "\trtsp://192.0.2.10/main\n")
        assertEquals(emptySet<CameraError>(), CameraValidator.validate(c))
    }

    @Test
    fun blankName() {
        assertEquals(setOf(CameraError.NAME_BLANK), CameraValidator.validate(camera(name = "")))
        assertEquals(setOf(CameraError.NAME_BLANK), CameraValidator.validate(camera(name = "  \t")))
    }

    @Test
    fun blankGridUrl() {
        assertTrue(CameraValidator.validate(camera(gridUrl = "")).contains(CameraError.GRID_URL_BLANK))
        assertTrue(CameraValidator.validate(camera(gridUrl = "   ")).contains(CameraError.GRID_URL_BLANK))
    }

    @Test
    fun blankGridUrlReportsOnlyGridUrlBlank() {
        assertEquals(setOf(CameraError.GRID_URL_BLANK), CameraValidator.validate(camera(gridUrl = "")))
        assertEquals(setOf(CameraError.GRID_URL_BLANK), CameraValidator.validate(camera(gridUrl = " \t ")))
    }

    @Test
    fun invalidGridUrl() {
        assertEquals(setOf(CameraError.GRID_URL_INVALID), CameraValidator.validate(camera(gridUrl = "ftp://192.0.2.10/x")))
        assertEquals(setOf(CameraError.GRID_URL_INVALID), CameraValidator.validate(camera(gridUrl = "rtsp:///x")))
        assertEquals(setOf(CameraError.GRID_URL_INVALID), CameraValidator.validate(camera(gridUrl = "rtsp://192.0.2.10/a b")))
    }

    @Test
    fun invalidDetailUrl() {
        assertEquals(setOf(CameraError.DETAIL_URL_INVALID), CameraValidator.validate(camera(detailUrl = "192.0.2.10/main")))
        assertEquals(setOf(CameraError.DETAIL_URL_INVALID), CameraValidator.validate(camera(detailUrl = "rtsp://")))
        assertEquals(setOf(CameraError.DETAIL_URL_INVALID), CameraValidator.validate(camera(detailUrl = "rtsp://192.0.2.10/a b")))
    }

    @Test
    fun reportsEveryProblem() {
        val c = camera(name = " ", gridUrl = "ftp://192.0.2.10/x", detailUrl = "nope")
        assertEquals(
            setOf(CameraError.NAME_BLANK, CameraError.GRID_URL_INVALID, CameraError.DETAIL_URL_INVALID),
            CameraValidator.validate(c),
        )
    }

    @Test
    fun reportsBlankNameBlankGridUrlAndInvalidDetailUrlExactly() {
        assertEquals(
            setOf(CameraError.NAME_BLANK, CameraError.GRID_URL_BLANK, CameraError.DETAIL_URL_INVALID),
            CameraValidator.validate(camera(name = "", gridUrl = "", detailUrl = "bad url")),
        )
    }

    @Test
    fun reportsBlankNameAndBlankGridUrlTogether() {
        val errors = CameraValidator.validate(camera(name = "", gridUrl = "", detailUrl = "bad url"))
        assertTrue(errors.contains(CameraError.NAME_BLANK))
        assertTrue(errors.contains(CameraError.GRID_URL_BLANK))
        assertTrue(errors.contains(CameraError.DETAIL_URL_INVALID))
    }
}
