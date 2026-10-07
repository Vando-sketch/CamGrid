package io.github.vandosketch.camgrid.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class Go2rtcClientTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Go2rtcClient =
        Go2rtcClient(
            createCamGridHttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            ),
        )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun returnsStreamNamesInServerOrder() = runTest {
        val names = client { json("""{"kitchen":{},"door":{},"garden":{}}""") }
            .fetchStreamNames("http://192.0.2.10:1984")

        assertEquals(listOf("kitchen", "door", "garden"), names)
        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("http://192.0.2.10:1984/api/streams", request.url.toString())
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
    }

    @Test
    fun addsSchemeAndToleratesTrailingSlash() = runTest {
        client { json("{}") }.fetchStreamNames("192.0.2.10:1984/")
        assertEquals("http://192.0.2.10:1984/api/streams", requests.single().url.toString())
    }

    @Test
    fun sendsUserInfoAsBasicAuthAndNotInTheUrl() = runTest {
        client { json("{}") }.fetchStreamNames("http://admin:p%40ss@192.0.2.10:1984")

        val request = requests.single()
        // "admin:p@ss" in Base64.
        assertEquals("Basic YWRtaW46cEBzcw==", request.headers[HttpHeaders.Authorization])
        assertEquals("http://192.0.2.10:1984/api/streams", request.url.toString())
    }

    @Test
    fun sendsNoAuthorizationWithoutUserInfo() = runTest {
        client { json("{}") }.fetchStreamNames("http://192.0.2.10:1984")
        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun errorStatusIsHttpStatusWithCode() = runTest {
        val e = assertFailsWith<Go2rtcException> {
            client { json("nope", HttpStatusCode.Unauthorized) }.fetchStreamNames("http://u:secret@192.0.2.10:1984")
        }
        assertEquals(Go2rtcException.Reason.HTTP_STATUS, e.reason)
        assertEquals("HTTP 401", e.detail)
        assertNoUrlIn(e)
    }

    @Test
    fun redirectsAreNotFollowed() = runTest {
        val e = assertFailsWith<Go2rtcException> {
            client {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://example.com/api/streams"))
            }.fetchStreamNames("http://u:secret@192.0.2.10:1984")
        }
        assertEquals(Go2rtcException.Reason.HTTP_STATUS, e.reason)
        assertEquals("HTTP 302", e.detail)
        assertEquals(1, requests.size)
    }

    @Test
    fun bodyThatIsNotAJsonObjectIsNotGo2rtc() = runTest {
        val e = assertFailsWith<Go2rtcException> {
            client { json("<html>router login</html>") }.fetchStreamNames("http://192.0.2.10:1984")
        }
        assertEquals(Go2rtcException.Reason.NOT_GO2RTC, e.reason)
    }

    @Test
    fun oversizedBodyIsRejectedWithoutParsing() = runTest {
        val huge = "{\"" + "a".repeat(Go2rtcClient.MAX_RESPONSE_BYTES) + "\":{}}"
        val e = assertFailsWith<Go2rtcException> {
            client { json(huge) }.fetchStreamNames("http://192.0.2.10:1984")
        }
        assertEquals(Go2rtcException.Reason.NOT_GO2RTC, e.reason)
        assertEquals("TOO_LARGE", e.detail)
    }

    @Test
    fun bodyAtTheLimitIsAccepted() = runTest {
        val name = "a".repeat(Go2rtcClient.MAX_RESPONSE_BYTES - 7)
        val body = "{\"$name\":{}}"
        assertEquals(Go2rtcClient.MAX_RESPONSE_BYTES, body.length)
        assertEquals(listOf(name), client { json(body) }.fetchStreamNames("http://192.0.2.10:1984"))
    }

    @Test
    fun networkFailureIsNetworkWithTheExceptionTypeOnly() = runTest {
        val e = assertFailsWith<Go2rtcException> {
            client { throw IOException("connect to http://u:secret@192.0.2.10:1984 failed") }
                .fetchStreamNames("http://u:secret@192.0.2.10:1984")
        }
        assertEquals(Go2rtcException.Reason.NETWORK, e.reason)
        assertEquals("IOException", e.detail)
        assertNoUrlIn(e)
    }

    @Test
    fun unexpectedEngineFailureIsNetwork() = runTest {
        val e = assertFailsWith<Go2rtcException> {
            client { throw IllegalStateException("http://u:secret@192.0.2.10") }.fetchStreamNames("http://192.0.2.10:1984")
        }
        assertEquals(Go2rtcException.Reason.NETWORK, e.reason)
        assertEquals("IllegalStateException", e.detail)
        assertNoUrlIn(e)
    }

    @Test
    fun invalidUrlsFailWithoutARequest() = runTest {
        val client = client { json("{}") }
        for (url in listOf("", "   ", "ftp://192.0.2.10", "http://", "http://192.0.2.10 1984", "http:///api")) {
            val e = assertFailsWith<Go2rtcException>(url) { client.fetchStreamNames(url) }
            assertEquals(Go2rtcException.Reason.INVALID_URL, e.reason, url)
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun httpsIsAccepted() = runTest {
        client { json("{}") }.fetchStreamNames("https://cams.example.com")
        assertEquals("https://cams.example.com/api/streams", requests.single().url.toString())
    }

    @Test
    fun timeoutsAreFiveSeconds() {
        assertEquals(5_000L, CAMGRID_HTTP_TIMEOUT_MS)
    }

    private fun assertNoUrlIn(e: Exception) {
        val text = listOfNotNull(e.message, (e as? Go2rtcException)?.detail).joinToString(" ")
        assertFalse("secret" in text || "192.0.2" in text, text)
    }
}
