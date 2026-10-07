package io.github.vandosketch.camgrid.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamSourcePlanTest {

    private val main = "http://192.0.2.10:1984/api/webrtc?src=door_main"
    private val sub = "http://192.0.2.10:1984/api/webrtc?src=door_sub"

    private fun plan(
        url: String = main,
        type: StreamType = StreamType.WEBRTC,
        audio: Boolean = true,
        lowerResolutionUrl: String? = null,
    ) = StreamSourcePlan(url, type, audio, lowerResolutionUrl)

    @Test
    fun startsWithTheCameraUrl() {
        val p = plan(url = " $main ")
        assertEquals(StreamSourcePlan.Source(main, StreamType.WEBRTC), p.current)
        assertFalse(p.current.isFallback)
    }

    // Issue #17: go2rtc cannot send the camera's codec (H.265) over WebRTC.

    @Test
    fun webrtcCodecRejectionSwitchesToGo2rtcMp4() {
        val p = plan()
        val next = p.onFailure("CODEC_H265")
        val expected = StreamSourcePlan.Source(
            url = "http://192.0.2.10:1984/api/stream.mp4?src=door_main&mp4=flac",
            type = StreamType.RTSP,
            viaMp4 = true,
            codec = "H265",
        )
        assertEquals(expected, next)
        assertEquals(expected, p.current)
        assertTrue(p.current.isFallback)
    }

    @Test
    fun gridTileMp4IsVideoOnly() {
        val next = plan(audio = false).onFailure(StreamFailures.CODEC_UNSUPPORTED)
        assertEquals("http://192.0.2.10:1984/api/stream.mp4?src=door_main&video=h264,h265", next?.url)
        assertNull(next?.codec)
    }

    @Test
    fun otherFailuresKeepTheSource() {
        val p = plan(lowerResolutionUrl = sub)
        for (reason in listOf("TIMEOUT", "STALLED", "ICE_FAILED", "HTTP_404", "SOURCE_TIMEOUT", "IOException", "DECODING_RESOURCES_RECLAIMED")) {
            assertNull(p.onFailure(reason), reason)
        }
        assertEquals(StreamSourcePlan.Source(main, StreamType.WEBRTC), p.current)
    }

    @Test
    fun mp4IsTriedOnlyOnce() {
        val p = plan()
        p.onFailure("CODEC_H265")
        val mp4 = p.current
        assertNull(p.onFailure("CODEC_H265"))
        assertNull(p.onFailure("RTSP_5XX"))
        assertEquals(mp4, p.current)
    }

    @Test
    fun rtspStreamsIgnoreCodecCodes() {
        val p = plan(url = "rtsp://192.0.2.10:8554/door_main", type = StreamType.RTSP)
        assertNull(p.onFailure("CODEC_H265"))
    }

    @Test
    fun codecRejectionFromAnotherWhepServerUsesTheLowerResolution() {
        val whep = "http://192.0.2.10:8889/door/whep"
        val lower = "http://192.0.2.10:8889/door_sub/whep"
        assertNull(plan(url = whep).onFailure("CODEC_UNSUPPORTED"))
        assertEquals(
            StreamSourcePlan.Source(lower, StreamType.WEBRTC, lowerResolution = true),
            plan(url = whep, lowerResolutionUrl = lower).onFailure("CODEC_UNSUPPORTED"),
        )
    }

    // Issue #16: the device's decoder cannot play the fullscreen stream (portrait 1536x2048 on a 1080p decoder).

    @Test
    fun decoderFailureSwitchesToTheLowerResolution() {
        val p = plan(url = "rtsp://192.0.2.10:8554/door_main", type = StreamType.RTSP, lowerResolutionUrl = " rtsp://192.0.2.10:8554/door_sub ")
        assertEquals(
            StreamSourcePlan.Source("rtsp://192.0.2.10:8554/door_sub", StreamType.RTSP, lowerResolution = true),
            p.onFailure("DECODING_FORMAT_EXCEEDS_CAPABILITIES"),
        )
        // There is nothing lower than the grid stream.
        assertNull(p.onFailure("DECODER_INIT_FAILED"))
    }

    @Test
    fun decoderStartFailureSwitchesOnlyWhenItRepeats() {
        // The grid's decoders may still be being freed when fullscreen starts its own.
        val lower = StreamSourcePlan.Source(sub, StreamType.WEBRTC, lowerResolution = true)
        val p = plan(lowerResolutionUrl = sub)
        assertNull(p.onFailure("DECODER_INIT_FAILED"))
        assertEquals(lower, p.onFailure("DECODER_INIT_FAILED"))

        val interrupted = plan(lowerResolutionUrl = sub)
        assertNull(interrupted.onFailure("DECODER_INIT_FAILED"))
        interrupted.onPlaying()
        assertNull(interrupted.onFailure("DECODER_INIT_FAILED"))
        assertNull(interrupted.onFailure("TIMEOUT"))
        assertNull(interrupted.onFailure("DECODING_FAILED"))
        assertEquals(lower, interrupted.onFailure("DECODING_FAILED"))
    }

    @Test
    fun decoderFailureWithoutAnotherStreamKeepsTheSource() {
        assertNull(plan().onFailure("DECODER_INIT_FAILED"))
        assertNull(plan(lowerResolutionUrl = "  ").onFailure("DECODER_INIT_FAILED"))
        // A camera without a detail URL plays its grid URL in fullscreen too.
        assertNull(plan(lowerResolutionUrl = " $main").onFailure("DECODER_INIT_FAILED"))
    }

    @Test
    fun h265MainStreamTooBigForTheDecoderEndsOnTheGridStream() {
        val p = plan(lowerResolutionUrl = sub)
        assertEquals(StreamType.RTSP, p.onFailure("CODEC_H265")?.type)
        // The MP4 of the main stream exceeds the decoder, so the grid stream is next ...
        assertEquals(StreamSourcePlan.Source(sub, StreamType.WEBRTC, lowerResolution = true), p.onFailure("DECODER_NO_OUTPUT"))
        // ... which is H.265 too and goes to MP4 as well.
        assertEquals(
            StreamSourcePlan.Source(
                url = "http://192.0.2.10:1984/api/stream.mp4?src=door_sub&mp4=flac",
                type = StreamType.RTSP,
                viaMp4 = true,
                lowerResolution = true,
                codec = "H265",
            ),
            p.onFailure("CODEC_H265"),
        )
        assertNull(p.onFailure("DECODER_INIT_FAILED"))
    }

    @Test
    fun sourceTextNeverShowsTheUrl() {
        val p = plan(url = "http://viewer:secret@192.0.2.10:1984/api/webrtc?src=door_main")
        p.onFailure("CODEC_H265")
        val text = p.current.toString()
        assertFalse("secret" in text, text)
        assertFalse("192.0.2.10" in text, text)
        assertEquals("MP4 (H265)", text)
        assertEquals("WEBRTC", plan().current.toString())
    }

    @Test
    fun failureClasses() {
        for (code in listOf("CODEC_H265", "CODEC_UNSUPPORTED")) assertTrue(StreamFailures.isCodecRejection(code), code)
        for (code in listOf("HTTP_500", "TIMEOUT", "NO_DECODER")) assertFalse(StreamFailures.isCodecRejection(code), code)
        assertEquals("H265", StreamFailures.codecOf("CODEC_H265"))
        assertNull(StreamFailures.codecOf("CODEC_UNSUPPORTED"))
        assertNull(StreamFailures.codecOf("TIMEOUT"))
        val decoder = listOf(
            "DECODER_INIT_FAILED", "DECODER_QUERY_FAILED", "DECODING_FAILED", "DECODING_FORMAT_EXCEEDS_CAPABILITIES",
            "DECODING_FORMAT_UNSUPPORTED", "DECODER_NO_OUTPUT", "DECODER_ERROR", "NO_DECODER",
        )
        for (code in decoder) assertTrue(StreamFailures.isDecoderFailure(code), code)
        for (code in listOf("DECODING_RESOURCES_RECLAIMED", "TIMEOUT", "CODEC_H265", "HTTP_500")) {
            assertFalse(StreamFailures.isDecoderFailure(code), code)
        }
        for (code in listOf("DECODING_FORMAT_EXCEEDS_CAPABILITIES", "DECODING_FORMAT_UNSUPPORTED", "DECODER_NO_OUTPUT", "DECODER_ERROR")) {
            assertTrue(StreamFailures.isDefiniteDecoderFailure(code), code)
        }
        for (code in listOf("DECODER_INIT_FAILED", "DECODING_FAILED", "NO_DECODER")) assertFalse(StreamFailures.isDefiniteDecoderFailure(code), code)
    }
}
