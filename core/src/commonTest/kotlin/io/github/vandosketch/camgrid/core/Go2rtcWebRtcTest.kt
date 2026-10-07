package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/** go2rtc helpers for WebRTC: endpoint URLs, import and converting a camera between types. */
class Go2rtcWebRtcTest {

    private val base = "http://192.0.2.10:1984"

    // webrtcUrl

    @Test
    fun webrtcUrl_appendsApiPathAndSource() {
        assertEquals("http://192.0.2.10:1984/api/webrtc?src=front_sub", Go2rtc.webrtcUrl(base, "front_sub"))
    }

    @Test
    fun webrtcUrl_toleratesTrailingSlashAndMissingScheme() {
        assertEquals("http://192.0.2.10:1984/api/webrtc?src=a", Go2rtc.webrtcUrl("192.0.2.10:1984/", "a"))
    }

    @Test
    fun webrtcUrl_keepsHttpsAndUserInfo() {
        assertEquals(
            "https://viewer:secret@cam.example.com/api/webrtc?src=a",
            Go2rtc.webrtcUrl("https://viewer:secret@cam.example.com", "a"),
        )
    }

    @Test
    fun webrtcUrl_percentEncodesTheName() {
        assertEquals("http://192.0.2.10:1984/api/webrtc?src=front%20door%26x", Go2rtc.webrtcUrl(base, "front door&x"))
    }

    // suggestCameras

    @Test
    fun suggest_defaultsToRtsp() {
        val camera = Go2rtc.suggestCameras(base, listOf("front_sub", "front_main")).single()
        assertEquals(StreamType.RTSP, camera.streamType)
        assertEquals("rtsp://192.0.2.10:8554/front_sub", camera.gridUrl)
    }

    @Test
    fun suggest_webrtcUsesApiUrls() {
        val camera = Go2rtc.suggestCameras(base, listOf("front_sub", "front_main"), streamType = StreamType.WEBRTC).single()
        assertEquals(
            Camera(
                id = "go2rtc:front",
                name = "front",
                gridUrl = "http://192.0.2.10:1984/api/webrtc?src=front_sub",
                detailUrl = "http://192.0.2.10:1984/api/webrtc?src=front_main",
                streamType = StreamType.WEBRTC,
            ),
            camera,
        )
    }

    @Test
    fun suggest_webrtcSingleStreamHasBlankDetail() {
        val camera = Go2rtc.suggestCameras(base, listOf("garage"), streamType = StreamType.WEBRTC).single()
        assertEquals("http://192.0.2.10:1984/api/webrtc?src=garage", camera.gridUrl)
        assertEquals("", camera.detailUrl)
    }

    // convertUrl: RTSP -> WebRTC

    @Test
    fun convert_go2rtcRtspToWebrtc() {
        assertEquals(
            "http://192.0.2.10:1984/api/webrtc?src=front_sub",
            Go2rtc.convertUrl("rtsp://192.0.2.10:8554/front_sub", StreamType.WEBRTC),
        )
    }

    @Test
    fun convert_rtspKeepsUserInfoAndEncodedName() {
        assertEquals(
            "http://viewer:secret@192.0.2.10:1984/api/webrtc?src=front%20door",
            Go2rtc.convertUrl("rtsp://viewer:secret@192.0.2.10:8554/front%20door", StreamType.WEBRTC),
        )
    }

    @Test
    fun convert_rtspToWebrtcCustomPorts() {
        assertEquals(
            "http://192.0.2.10:11984/api/webrtc?src=a",
            Go2rtc.convertUrl("rtsp://192.0.2.10:18554/a", StreamType.WEBRTC, apiPort = 11984, rtspPort = 18554),
        )
    }

    @Test
    fun convert_rtspThatIsNotGo2rtcIsNull() {
        // A camera's own RTSP server: no go2rtc behind it, so there is no WebRTC endpoint to guess.
        assertNull(Go2rtc.convertUrl("rtsp://192.0.2.20:554/Streaming/Channels/101", StreamType.WEBRTC))
        assertNull(Go2rtc.convertUrl("rtsp://192.0.2.20/a", StreamType.WEBRTC))
    }

    @Test
    fun convert_rtspWithNestedPathOrQueryIsNull() {
        assertNull(Go2rtc.convertUrl("rtsp://192.0.2.10:8554/a/b", StreamType.WEBRTC))
        assertNull(Go2rtc.convertUrl("rtsp://192.0.2.10:8554/a?mp4", StreamType.WEBRTC))
        assertNull(Go2rtc.convertUrl("rtsp://192.0.2.10:8554/", StreamType.WEBRTC))
    }

    @Test
    fun convert_ipv6Host() {
        assertEquals(
            "http://[2001:db8::1]:1984/api/webrtc?src=a",
            Go2rtc.convertUrl("rtsp://[2001:db8::1]:8554/a", StreamType.WEBRTC),
        )
    }

    // convertUrl: WebRTC -> RTSP

    @Test
    fun convert_go2rtcWebrtcToRtsp() {
        assertEquals(
            "rtsp://192.0.2.10:8554/front_sub",
            Go2rtc.convertUrl("http://192.0.2.10:1984/api/webrtc?src=front_sub", StreamType.RTSP),
        )
    }

    @Test
    fun convert_webrtcToRtspKeepsUserInfoAndEncoding() {
        assertEquals(
            "rtsp://viewer:secret@192.0.2.10:8554/front%20door",
            Go2rtc.convertUrl("https://viewer:secret@192.0.2.10:1984/api/webrtc?src=front+door", StreamType.RTSP),
        )
    }

    @Test
    fun convert_webrtcWithoutSourceIsNull() {
        assertNull(Go2rtc.convertUrl("http://192.0.2.10:1984/api/webrtc", StreamType.RTSP))
        assertNull(Go2rtc.convertUrl("http://192.0.2.10:1984/api/webrtc?dst=a", StreamType.RTSP))
        assertNull(Go2rtc.convertUrl("http://192.0.2.10:1984/api/webrtc?src=", StreamType.RTSP))
    }

    @Test
    fun convert_otherHttpUrlIsNull() {
        assertNull(Go2rtc.convertUrl("https://cam.example.com/live.m3u8", StreamType.RTSP))
        assertNull(Go2rtc.convertUrl("http://192.0.2.10:8889/front/whep", StreamType.RTSP))
    }

    @Test
    fun convert_findsSourceAmongOtherParameters() {
        assertEquals(
            "rtsp://192.0.2.10:8554/b",
            Go2rtc.convertUrl("http://192.0.2.10:1984/api/webrtc?x=1&src=b", StreamType.RTSP),
        )
    }

    // convertUrl: no change needed, or nothing to convert

    @Test
    fun convert_alreadyTargetTypeIsUnchanged() {
        val rtsp = "rtsp://192.0.2.10:8554/a"
        val webrtc = "http://192.0.2.10:1984/api/webrtc?src=a"
        assertEquals(rtsp, Go2rtc.convertUrl(rtsp, StreamType.RTSP))
        assertEquals(webrtc, Go2rtc.convertUrl(webrtc, StreamType.WEBRTC))
    }

    @Test
    fun convert_blankOrGarbageIsNull() {
        assertNull(Go2rtc.convertUrl("", StreamType.WEBRTC))
        assertNull(Go2rtc.convertUrl("not a url", StreamType.RTSP))
    }

    @Test
    fun convert_roundTrip() {
        val rtsp = "rtsp://viewer:secret@192.0.2.10:8554/front%20door_sub"
        val webrtc = Go2rtc.convertUrl(rtsp, StreamType.WEBRTC)!!
        assertEquals(rtsp, Go2rtc.convertUrl(webrtc, StreamType.RTSP))
    }

    @Test
    fun convert_trimsInput() {
        assertEquals(
            "http://192.0.2.10:1984/api/webrtc?src=a",
            Go2rtc.convertUrl("  rtsp://192.0.2.10:8554/a ", StreamType.WEBRTC),
        )
    }
}
