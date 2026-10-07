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

    // go2rtc's error texts (internal/streams/add_consumer.go, internal/webrtc/server.go)

    @Test
    fun go2rtcCodecMismatchNamesTheCameraCodec() {
        // What go2rtc 1.9 answers an H.264-only offer for an H.265 camera.
        val e = failure {
            WebRtcSignaling.parseAnswer(500, "text/plain; charset=utf-8", "streams: codecs not matched: video:H265, audio:PCMA => video:H264\n")
        }
        assertEquals(SignalingException.Reason.CODEC, e.reason)
        assertEquals("CODEC_H265", e.code)
    }

    @Test
    fun codecMismatchTakesTheFirstVideoCodec() {
        assertEquals(
            "CODEC_H265",
            WebRtcSignaling.errorCode(500, "streams: codecs not matched: audio:AAC, video:H265, video:MJPEG => video:H264, video:VP8, audio:opus"),
        )
    }

    @Test
    fun codecMismatchWithoutVideoCodecIsUnsupported() {
        assertEquals(StreamFailures.CODEC_UNSUPPORTED, WebRtcSignaling.errorCode(500, "streams: codecs not matched:  => video:H264"))
        assertEquals(StreamFailures.CODEC_UNSUPPORTED, WebRtcSignaling.errorCode(500, "streams: codecs not matched"))
    }

    @Test
    fun onlyPlainShortCodecNamesAreTakenOver() {
        assertEquals("CODEC_VP9", WebRtcSignaling.errorCode(500, "streams: codecs not matched: video:vp9 => video:H264"))
        assertEquals(StreamFailures.CODEC_UNSUPPORTED, WebRtcSignaling.errorCode(500, "streams: codecs not matched: video:x-h265/long => video:H264"))
        assertEquals(StreamFailures.CODEC_UNSUPPORTED, WebRtcSignaling.errorCode(500, "streams: codecs not matched: video:ABCDEFGHIJK => video:H264"))
    }

    @Test
    fun go2rtcSourceErrorsGetAReadableCodeWithoutDetails() {
        assertEquals("SOURCE_TIMEOUT", WebRtcSignaling.errorCode(500, "streams: dial tcp 192.0.2.20:554: i/o timeout"))
        assertEquals("SOURCE_REFUSED", WebRtcSignaling.errorCode(500, "streams: dial tcp 192.0.2.20:554: connect: connection refused"))
        assertEquals("SOURCE_UNAUTHORIZED", WebRtcSignaling.errorCode(500, "streams: wrong response on DESCRIBE: RTSP/1.0 401 Unauthorized"))
        assertEquals("SOURCE_FAILED", WebRtcSignaling.errorCode(500, "streams: rtsp://admin:secret@192.0.2.20/main EOF"))
        assertEquals("SOURCE_FAILED", WebRtcSignaling.errorCode(500, "streams: unknown error"))
    }

    @Test
    fun otherErrorsKeepTheStatus() {
        assertEquals("HTTP_404", WebRtcSignaling.errorCode(404, "stream not found"))
        assertEquals("HTTP_500", WebRtcSignaling.errorCode(500, ""))
        assertEquals("HTTP_500", WebRtcSignaling.errorCode(500, "webrtc: something else"))
        assertEquals("HTTP_502", WebRtcSignaling.errorCode(502, "<html>Bad Gateway</html>"))
        assertEquals("HTTP_401", WebRtcSignaling.errorCode(401, "streams: dial tcp: i/o timeout"))
    }

    @Test
    fun errorCodeNeverContainsTheBody() {
        val code = WebRtcSignaling.errorCode(500, "streams: codecs not matched: video:rtsp://viewer:secret@192.0.2.10/a => video:H264")
        assertEquals(StreamFailures.CODEC_UNSUPPORTED, code)
    }

    // An answer that accepts audio but not video: go2rtc's reply when only the audio codec matched.

    private fun answer(vararg sections: String) = "v=0\r\no=- 1 1 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\n" + sections.joinToString("")

    private val inactiveVideo = "m=video 9 UDP/TLS/RTP/SAVPF 96\r\na=rtpmap:96 H264/90000\r\na=inactive\r\n"
    private val sendingVideo = "m=video 9 UDP/TLS/RTP/SAVPF 96\r\na=rtpmap:96 H264/90000\r\na=sendonly\r\n"
    private val sendingAudio = "m=audio 9 UDP/TLS/RTP/SAVPF 8\r\na=rtpmap:8 PCMA/8000\r\na=sendonly\r\n"

    @Test
    fun inactiveVideoIsACodecRejection() {
        val e = failure { WebRtcSignaling.parseAnswer(201, "application/sdp", answer(inactiveVideo, sendingAudio)) }
        assertEquals(SignalingException.Reason.CODEC, e.reason)
        assertEquals(StreamFailures.CODEC_UNSUPPORTED, e.code)
    }

    @Test
    fun rejectedVideoPortIsACodecRejection() {
        val rejected = "m=video 0 UDP/TLS/RTP/SAVPF 96\r\na=sendonly\r\n"
        assertEquals(SignalingException.Reason.CODEC, failure { WebRtcSignaling.parseAnswer(201, null, answer(rejected, sendingAudio)) }.reason)
    }

    @Test
    fun sessionLevelInactiveAppliesToVideo() {
        val sdp = "v=0\r\ns=-\r\nt=0 0\r\na=inactive\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n"
        assertEquals(SignalingException.Reason.CODEC, failure { WebRtcSignaling.parseAnswer(201, null, sdp) }.reason)
    }

    @Test
    fun oneSendingVideoSectionIsEnough() {
        val sdp = answer(inactiveVideo, sendingVideo)
        assertEquals(sdp, WebRtcSignaling.parseAnswer(201, null, sdp))
    }

    @Test
    fun sendingVideoIsAccepted() {
        val sdp = answer(sendingVideo, sendingAudio)
        assertEquals(sdp, WebRtcSignaling.parseAnswer(201, "application/sdp", sdp))
        // No direction attribute means sendrecv.
        val plain = answer("m=video 9 UDP/TLS/RTP/SAVPF 96\r\n")
        assertEquals(plain, WebRtcSignaling.parseAnswer(201, "application/sdp", plain))
    }

    @Test
    fun answerWithoutVideoSectionIsNotJudged() {
        val sdp = answer(sendingAudio)
        assertEquals(sdp, WebRtcSignaling.parseAnswer(200, null, sdp))
    }
}
