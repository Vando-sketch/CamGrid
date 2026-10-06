package io.github.vandosketch.camgrid.data

import io.github.vandosketch.camgrid.core.SignalingException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class WhepClientTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): WhepClient =
        WhepClient(
            createCamGridHttpClient(
                MockEngine { request ->
                    requests += request
                    bodies += request.body.toByteArray().decodeToString()
                    handler(request)
                },
            ),
        )

    private fun MockRequestHandleScope.sdp(body: String, status: HttpStatusCode = HttpStatusCode.Created) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/sdp"))

    @Test
    fun postsTheOfferAndReturnsTheAnswer() = runTest {
        val answer = client { sdp("v=0\r\nanswer") }.exchange("http://192.0.2.10:1984/api/webrtc?src=kitchen", "v=0\r\noffer")

        assertEquals("v=0\r\nanswer", answer)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://192.0.2.10:1984/api/webrtc?src=kitchen", request.url.toString())
        assertEquals("application/sdp", request.body.contentType.toString())
        assertEquals("application/sdp", request.headers[HttpHeaders.Accept])
        assertEquals("v=0\r\noffer", bodies.single())
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun acceptsGo2rtcJsonAnswerWith200() = runTest {
        val answer = client {
            respond("""{"type":"answer","sdp":"v=0\r\nx"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }.exchange("http://192.0.2.10:1984/api/webrtc?src=a", "v=0")
        assertEquals("v=0\r\nx", answer)
    }

    @Test
    fun sendsUserInfoAsBasicAuthAndNotInTheUrl() = runTest {
        client { sdp("v=0") }.exchange("  http://admin:p%40ss@192.0.2.10:8889/cam/whep  ", "v=0")

        val request = requests.single()
        assertEquals("Basic YWRtaW46cEBzcw==", request.headers[HttpHeaders.Authorization])
        assertEquals("http://192.0.2.10:8889/cam/whep", request.url.toString())
    }

    @Test
    fun userWithoutPasswordIsSentAsIs() = runTest {
        client { sdp("v=0") }.exchange("http://viewer@192.0.2.10:8889/cam/whep", "v=0")
        // "viewer" in Base64, without a colon, as Android sent it before.
        assertEquals("Basic dmlld2Vy", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun errorStatusIsSignalingHttpStatus() = runTest {
        val e = assertFailsWith<SignalingException> {
            client { sdp("stream not found", HttpStatusCode.NotFound) }.exchange("http://u:secret@192.0.2.10:1984/api/webrtc?src=x", "v=0")
        }
        assertEquals(SignalingException.Reason.HTTP_STATUS, e.reason)
        assertEquals("HTTP_404", e.code)
    }

    @Test
    fun answerOverTheLimitIsBadAnswer() = runTest {
        val e = assertFailsWith<SignalingException> {
            client { sdp("v=0\r\n" + "a".repeat(WhepClient.MAX_ANSWER_BYTES)) }.exchange("http://192.0.2.10:1984/api/webrtc?src=x", "v=0")
        }
        assertEquals(SignalingException.Reason.BAD_ANSWER, e.reason)
    }

    @Test
    fun answerAtTheLimitIsAccepted() = runTest {
        val body = "v=0" + "a".repeat(WhepClient.MAX_ANSWER_BYTES - 3)
        assertEquals(body, client { sdp(body) }.exchange("http://192.0.2.10:1984/api/webrtc?src=x", "v=0"))
    }

    @Test
    fun garbageAnswerIsBadAnswer() = runTest {
        val e = assertFailsWith<SignalingException> {
            client { sdp("<html></html>", HttpStatusCode.OK) }.exchange("http://192.0.2.10:1984/api/webrtc?src=x", "v=0")
        }
        assertEquals(SignalingException.Reason.BAD_ANSWER, e.reason)
    }

    @Test
    fun invalidUrlIsIOExceptionWithoutRequest() = runTest {
        val client = client { sdp("v=0") }
        for (url in listOf("", "rtsp://192.0.2.10/x", "http://", "not a url", "http://192.0.2.10/a b")) {
            val e = assertFailsWith<IOException>(url) { client.exchange(url, "v=0") }
            assertEquals("Invalid URL", e.message)
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun networkFailureStaysAnIOExceptionWithoutTheUrl() = runTest {
        val e = assertFailsWith<IOException> {
            client { throw IllegalStateException("http://u:secret@192.0.2.10") }.exchange("http://u:secret@192.0.2.10/x", "v=0")
        }
        assertEquals("IllegalStateException", e.message)
    }

    @Test
    fun ioExceptionMessageIsReplacedByItsType() = runTest {
        val e = assertFailsWith<IOException> {
            client { throw IOException("connect to 192.0.2.10 timed out") }.exchange("http://192.0.2.10/x", "v=0")
        }
        assertEquals("IOException", e.message)
    }
}
