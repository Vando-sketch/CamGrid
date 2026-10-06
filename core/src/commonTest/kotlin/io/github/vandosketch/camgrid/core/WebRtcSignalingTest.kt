package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test

class WebRtcSignalingTest {

    private val sdp = "v=0\r\no=- 1 1 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n"

    private fun failure(block: () -> Unit): SignalingException =
        assertFailsWith<SignalingException> { block() }

    @Test
    fun requestContentType() {
        assertEquals("application/sdp", WebRtcSignaling.OFFER_CONTENT_TYPE)
    }

    // parseAnswer: accepted

    @Test
    fun rawSdpWith201() {
        // WHEP servers (MediaMTX, go2rtc) answer 201 Created with the SDP as the body.
        assertEquals(sdp, WebRtcSignaling.parseAnswer(201, "application/sdp", sdp))
    }

    @Test
    fun rawSdpWith200() {
        assertEquals(sdp, WebRtcSignaling.parseAnswer(200, "application/sdp", sdp))
    }

    @Test
    fun rawSdpIsRecognisedWithoutContentType() {
        assertEquals(sdp, WebRtcSignaling.parseAnswer(200, null, sdp))
        assertEquals(sdp, WebRtcSignaling.parseAnswer(200, "text/plain; charset=utf-8", sdp))
    }

    @Test
    fun leadingWhitespaceIsDropped() {
        assertEquals(sdp, WebRtcSignaling.parseAnswer(200, "application/sdp", "\n  $sdp"))
    }

    @Test
    fun jsonAnswer() {
        // go2rtc answers JSON when the offer was JSON; accept it in case a proxy rewrites the type.
        val json = """{"type":"answer","sdp":"v=0\r\no=- 1 1 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n"}"""
        assertEquals(sdp, WebRtcSignaling.parseAnswer(200, "application/json", json))
    }

    // parseAnswer: rejected

    @Test
    fun httpErrorReportsTheStatus() {
        val e = failure { WebRtcSignaling.parseAnswer(404, "text/plain", "stream not found") }
        assertEquals(SignalingException.Reason.HTTP_STATUS, e.reason)
        assertEquals("HTTP_404", e.code)
    }

    @Test
    fun redirectIsAnHttpError() {
        assertEquals("HTTP_302", failure { WebRtcSignaling.parseAnswer(302, null, "") }.code)
    }

    @Test
    fun serverErrorReportsTheStatus() {
        assertEquals("HTTP_500", failure { WebRtcSignaling.parseAnswer(500, null, sdp) }.code)
    }

    @Test
    fun emptyBodyIsABadAnswer() {
        val e = failure { WebRtcSignaling.parseAnswer(201, "application/sdp", "  ") }
        assertEquals(SignalingException.Reason.BAD_ANSWER, e.reason)
        assertEquals("BAD_ANSWER", e.code)
    }

    @Test
    fun htmlIsABadAnswer() {
        // For example a login page from a reverse proxy.
        assertEquals(
            SignalingException.Reason.BAD_ANSWER,
            failure { WebRtcSignaling.parseAnswer(200, "text/html", "<html>login</html>") }.reason,
        )
    }

    @Test
    fun jsonWithoutSdpIsABadAnswer() {
        assertEquals(
            SignalingException.Reason.BAD_ANSWER,
            failure { WebRtcSignaling.parseAnswer(200, "application/json", """{"error":"x"}""") }.reason,
        )
        assertEquals(
            SignalingException.Reason.BAD_ANSWER,
            failure { WebRtcSignaling.parseAnswer(200, "application/json", """{"type":"offer","sdp":"v=0\r\n"}""") }.reason,
        )
        assertEquals(
            SignalingException.Reason.BAD_ANSWER,
            failure { WebRtcSignaling.parseAnswer(200, "application/json", "[1,2]") }.reason,
        )
    }

    @Test
    fun malformedJsonIsABadAnswer() {
        assertEquals(
            SignalingException.Reason.BAD_ANSWER,
            failure { WebRtcSignaling.parseAnswer(200, "application/json", "{nope") }.reason,
        )
    }

    @Test
    fun messageNeverContainsTheBody() {
        // Bodies can echo the request URL, which may carry credentials.
        val e = failure { WebRtcSignaling.parseAnswer(401, null, "rtsp://viewer:secret@192.0.2.10/a") }
        assertEquals(false, e.message.orEmpty().contains("secret"))
    }
}
