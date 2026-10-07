package io.github.vandosketch.camgrid.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class WebRtcOfferExchangeTest {

    private val urls = mutableListOf<String>()
    private val bodies = mutableListOf<String>()

    private fun exchange(
        status: HttpStatusCode = HttpStatusCode.Created,
        failure: Exception? = null,
        body: String = "v=0\r\nanswer",
    ) =
        WebRtcOfferExchange(
            WhepClient(
                createCamGridHttpClient(
                    MockEngine { request ->
                        urls += request.url.toString()
                        bodies += request.body.toByteArray().decodeToString()
                        if (failure != null) throw failure
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/sdp"))
                    },
                ),
            ),
        )

    private val offer = listOf(
        "v=0",
        "m=video 9 UDP/TLS/RTP/SAVPF 96 98",
        "a=rtpmap:96 H264/90000",
        "a=fmtp:96 packetization-mode=1;profile-level-id=640c1f",
        "a=rtpmap:98 H264/90000",
        "a=fmtp:98 packetization-mode=1;profile-level-id=42e01f",
        "",
    ).joinToString("\r\n")

    @Test
    fun sendsTheOfferWithConstrainedBaselineFirst() = runTest {
        val result = exchange().exchange("http://192.0.2.10:1984/api/webrtc?src=kitchen", offer)

        assertEquals(WebRtcOfferExchange.Result.Answer("v=0\r\nanswer"), result)
        assertTrue(bodies.single().contains("m=video 9 UDP/TLS/RTP/SAVPF 98 96\r\n"))
    }

    @Test
    fun playerPageUrlsAreTurnedIntoTheSignallingUrl() = runTest {
        exchange().exchange("http://192.0.2.10:1984/stream.html?src=kitchen", offer)
        assertEquals("http://192.0.2.10:1984/api/webrtc?src=kitchen", urls.single())
    }

    @Test
    fun httpErrorsBecomeTheirCode() = runTest {
        val result = exchange(HttpStatusCode.NotFound).exchange("http://192.0.2.10:1984/api/webrtc?src=x", offer)
        assertEquals(WebRtcOfferExchange.Result.Failed("HTTP_404"), result)
    }

    @Test
    fun go2rtcCodecRejectionBecomesItsCode() = runTest {
        // The iOS player hands this code to the fallback: go2rtc's MP4 played by VLCKit (issue #17).
        val result = exchange(HttpStatusCode.InternalServerError, body = "streams: codecs not matched: video:H265 => video:H264")
            .exchange("http://192.0.2.10:1984/api/webrtc?src=x", offer)
        assertEquals(WebRtcOfferExchange.Result.Failed("CODEC_H265"), result)
    }

    @Test
    fun networkErrorsNeverCarryTheUrl() = runTest {
        val result = exchange(failure = IOException("connect to 192.0.2.10 timed out"))
            .exchange("http://192.0.2.10:1984/api/webrtc?src=x", offer)
        assertEquals(WebRtcOfferExchange.Result.Failed("IOException"), result)
    }

    @Test
    fun invalidUrl() = runTest {
        val result = exchange().exchange("not a url", offer)
        assertEquals(WebRtcOfferExchange.Result.Failed("Invalid URL"), result)
        assertTrue(urls.isEmpty())
    }
}
