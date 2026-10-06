package io.github.vandosketch.camgrid.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/** Connect and read timeout for every request: go2rtc and the cameras are on the LAN. */
const val CAMGRID_HTTP_TIMEOUT_MS = 5_000L

/**
 * The HTTP client for go2rtc and WebRTC signalling, on [engine]: errors are returned as
 * responses (not thrown), redirects are not followed (they could carry the Authorization
 * header to another host), and connecting or waiting for data times out after 5 s.
 */
fun createCamGridHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = CAMGRID_HTTP_TIMEOUT_MS
        socketTimeoutMillis = CAMGRID_HTTP_TIMEOUT_MS
    }
}

/** The platform's HTTP engine: HttpURLConnection on Android, java.net.http on desktop, NSURLSession on iOS. */
internal expect fun platformHttpEngine(): HttpClientEngine

/** One app-wide client on the platform's engine, created on first use. */
object CamGridHttp {
    val client: HttpClient by lazy { createCamGridHttpClient(platformHttpEngine()) }
}

/** Reads at most [maxBytes] from [channel] as UTF-8, or returns null when there is more. */
internal suspend fun readLimited(channel: ByteReadChannel, maxBytes: Int): String? {
    val bytes = channel.readRemaining(maxBytes + 1L).readByteArray()
    return if (bytes.size > maxBytes) null else bytes.decodeToString()
}
