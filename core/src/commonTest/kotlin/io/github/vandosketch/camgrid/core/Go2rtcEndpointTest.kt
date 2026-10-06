package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * The URL that "works in the browser" is usually go2rtc's player page, not its signalling
 * endpoint. [Go2rtc.webrtcEndpoint] turns any go2rtc page or API URL for a stream into
 * `/api/webrtc?src=<name>`, so a pasted browser URL works as a WebRTC camera URL.
 */
class Go2rtcEndpointTest {

    private val expected = "http://192.0.2.10:1984/api/webrtc?src=front_sub"

    @Test
    fun signallingUrlIsUnchanged() {
        assertEquals(expected, Go2rtc.webrtcEndpoint(expected))
    }

    @Test
    fun playerPages() {
        for (page in listOf("stream.html", "webrtc.html", "links.html")) {
            assertEquals(expected, Go2rtc.webrtcEndpoint("http://192.0.2.10:1984/$page?src=front_sub"), page)
        }
    }

    @Test
    fun playerPageWithModeAndOtherParameters() {
        assertEquals(
            expected,
            Go2rtc.webrtcEndpoint("http://192.0.2.10:1984/stream.html?mode=webrtc,mse&src=front_sub&media=video"),
        )
    }

    @Test
    fun otherApiEndpoints() {
        for (path in listOf("api/ws", "api/stream.mp4", "api/stream.m3u8", "api/frame.jpeg", "api/stream.mjpeg")) {
            assertEquals(expected, Go2rtc.webrtcEndpoint("http://192.0.2.10:1984/$path?src=front_sub"), path)
        }
    }

    @Test
    fun websocketSchemesBecomeHttp() {
        assertEquals(expected, Go2rtc.webrtcEndpoint("ws://192.0.2.10:1984/api/ws?src=front_sub"))
        assertEquals(
            "https://cam.example.com/api/webrtc?src=front_sub",
            Go2rtc.webrtcEndpoint("wss://cam.example.com/api/ws?src=front_sub"),
        )
    }

    @Test
    fun keepsHttpsUserInfoAndPathPrefix() {
        // go2rtc behind a reverse proxy under a sub-path.
        assertEquals(
            "https://viewer:secret@cam.example.com/go2rtc/api/webrtc?src=front_sub",
            Go2rtc.webrtcEndpoint("https://viewer:secret@cam.example.com/go2rtc/stream.html?src=front_sub"),
        )
    }

    @Test
    fun reEncodesTheName() {
        assertEquals(
            "http://192.0.2.10:1984/api/webrtc?src=front%20door",
            Go2rtc.webrtcEndpoint("http://192.0.2.10:1984/stream.html?src=front+door"),
        )
    }

    @Test
    fun dropsFragmentAndTrims() {
        assertEquals(expected, Go2rtc.webrtcEndpoint("  http://192.0.2.10:1984/stream.html?src=front_sub#x "))
    }

    @Test
    fun otherUrlsAreNull() {
        assertNull(Go2rtc.webrtcEndpoint("http://192.0.2.10:1984/"))
        assertNull(Go2rtc.webrtcEndpoint("http://192.0.2.10:1984/stream.html"))
        assertNull(Go2rtc.webrtcEndpoint("http://192.0.2.10:1984/stream.html?src="))
        assertNull(Go2rtc.webrtcEndpoint("http://192.0.2.10:8889/front/whep"))
        assertNull(Go2rtc.webrtcEndpoint("rtsp://192.0.2.10:8554/front_sub"))
        assertNull(Go2rtc.webrtcEndpoint("http://192.0.2.10:1984/api/streams?src=front_sub"))
        assertNull(Go2rtc.webrtcEndpoint("not a url"))
    }
}
