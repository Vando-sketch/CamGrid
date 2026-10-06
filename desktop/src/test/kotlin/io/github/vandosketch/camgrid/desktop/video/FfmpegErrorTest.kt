package io.github.vandosketch.camgrid.desktop.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil

class FfmpegErrorTest {

    // Values of FFmpeg's error macros (libavutil/error.h), as the C compiler computes them.
    private val averrorEof = -0x20464f45
    private val averrorExit = -0x54495845
    private val averrorHttpUnauthorized = -0x313034f8
    private val averrorHttpNotFound = -0x343034f8
    private val averrorHttpServerError = -0x585835f8

    @Test
    fun taggedErrorsHaveFixedCodes() {
        assertEquals("ENDED", Ffmpeg.tagCode(averrorEof))
        assertEquals("CANCELLED", Ffmpeg.tagCode(averrorExit))
        assertEquals("RTSP_401", Ffmpeg.tagCode(averrorHttpUnauthorized))
        assertEquals("RTSP_404", Ffmpeg.tagCode(averrorHttpNotFound))
        assertEquals("RTSP_5XX", Ffmpeg.tagCode(averrorHttpServerError))
    }

    /** Also proves the native FFmpeg libraries load on this OS (CI runs all three). */
    @Test
    fun matchesTheNativeLibrary() {
        assertEquals(avutil.AVERROR_EOF(), averrorEof)
        assertEquals(avutil.AVERROR_EXIT(), averrorExit)
        assertEquals(avutil.AVERROR_HTTP_UNAUTHORIZED(), averrorHttpUnauthorized)
        assertEquals(avutil.AVERROR_HTTP_NOT_FOUND(), averrorHttpNotFound)
        assertEquals(avutil.AVERROR_HTTP_SERVER_ERROR(), averrorHttpServerError)
        assertEquals("RTSP_401", Ffmpeg.errorCode(averrorHttpUnauthorized))
        val refused = Ffmpeg.errorCode(-ECONNREFUSED_LINUX)
        assertTrue(refused.matches(Regex("[A-Z0-9_]+")), refused)
        if (isLinux && System.getenv("LANG").orEmpty().let { it.isEmpty() || it.startsWith("C") || it.startsWith("en") }) {
            assertEquals("CONNECTION_REFUSED", refused)
        }
    }

    /** The build is the LGPL one: never the "-gpl" classifier. */
    @Test
    fun nativeFfmpegIsTheLgplBuild() {
        val license = avcodec.avcodec_license().string
        assertTrue(license.startsWith("LGPL"), license)
        val config = avcodec.avcodec_configuration().string
        println("FFmpeg licence: $license\nFFmpeg configuration: $config")
        assertFalse("--enable-gpl" in config, config)
        assertFalse("--enable-nonfree" in config, config)
    }

    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    private val ECONNREFUSED_LINUX = 111

    @Test
    fun errnoValuesAreNotTags() {
        assertNull(Ffmpeg.tagCode(-111))
        assertNull(Ffmpeg.tagCode(-110))
    }

    @Test
    fun errorTextBecomesUpperSnakeCase() {
        assertEquals("CONNECTION_REFUSED", Ffmpeg.snakeCase("Connection refused"))
        assertEquals("CONNECTION_TIMED_OUT", Ffmpeg.snakeCase("Connection timed out."))
        assertEquals("", Ffmpeg.snakeCase("  "))
        assertEquals(40, Ffmpeg.snakeCase("x".repeat(100)).length)
    }
}
