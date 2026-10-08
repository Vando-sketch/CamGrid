package io.github.vandosketch.camgrid.data

import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.ConfigCodec
import io.github.vandosketch.camgrid.core.ConfigSource
import io.github.vandosketch.camgrid.core.RemoteConfig
import io.github.vandosketch.camgrid.core.RemoteConfigException
import io.github.vandosketch.camgrid.core.RemoteConfigException.Reason
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class RemoteConfigClientTest {

    private val requests = mutableListOf<HttpRequestData>()

    private val hosted = CamGridConfig(
        views = listOf(CamView.uniform("main", "", 2, 1)),
        cameras = listOf(Camera("front", "Front", "rtsp://192.0.2.10:8554/front")),
    )

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        RemoteConfigClient(
            createCamGridHttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            ),
        )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun failure(source: ConfigSource, handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        assertFailsWith<RemoteConfigException> { client(handler).fetch(source) }

    @Test
    fun getsTheFileWithTheTokenAsBearerHeader() = runTest {
        val config = client { json(ConfigCodec.encode(hosted)) }
            .fetch(ConfigSource(" https://config.example.com/camgrid.json ", " s3cret "))

        assertEquals(hosted, config)
        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://config.example.com/camgrid.json", request.url.toString())
        assertEquals("Bearer s3cret", request.headers[HttpHeaders.Authorization])
        assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun sendsNoAuthorizationWithoutAToken() = runTest {
        client { json(ConfigCodec.encode(hosted)) }.fetch(ConfigSource("http://nas.local/camgrid.json"))
        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun refusesUnusableUrlsWithoutAnyRequest() = runTest {
        listOf("http://config.example.com/c.json", "https://u:p@config.example.com/c.json", "nas/c.json").forEach { url ->
            assertEquals(Reason.INVALID_URL, failure(ConfigSource(url)) { json("{}") }.reason, url)
        }
        assertEquals(Reason.INVALID_URL, failure(ConfigSource("https://nas/c.json", "a\r\nb")) { json("{}") }.reason)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun reportsTheHttpStatus() = runTest {
        val e = failure(ConfigSource("https://nas/c.json", "wrong")) { json("", HttpStatusCode.Unauthorized) }
        assertEquals(Reason.HTTP_STATUS, e.reason)
        assertEquals("HTTP 401", e.detail)
    }

    @Test
    fun aNetworkErrorIsReportedAsSuch() = runTest {
        val e = failure(ConfigSource("https://nas/c.json")) { throw IOException("connection reset by 192.0.2.10") }
        assertEquals(Reason.NETWORK, e.reason)
        assertEquals("IOException", e.detail)
    }

    @Test
    fun refusesAFileThatIsTooLarge() = runTest {
        val e = failure(ConfigSource("https://nas/c.json")) { json(" ".repeat(RemoteConfig.MAX_BYTES + 1)) }
        assertEquals(Reason.TOO_LARGE, e.reason)
    }

    @Test
    fun refusesWhatIsNotAConfig() = runTest {
        assertEquals(Reason.UNREADABLE, failure(ConfigSource("https://nas/c.json")) { json("<html>Login</html>") }.reason)
    }

    @Test
    fun refusesStreamUrlsWithPasswords() = runTest {
        val leaky = hosted.copy(cameras = listOf(Camera("front", "Front", "rtsp://admin:pw@192.0.2.10/front")))
        assertEquals(Reason.CREDENTIALS, failure(ConfigSource("https://nas/c.json")) { json(ConfigCodec.encode(leaky)) }.reason)
    }
}
