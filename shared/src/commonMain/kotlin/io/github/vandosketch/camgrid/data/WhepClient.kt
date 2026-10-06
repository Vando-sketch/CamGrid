package io.github.vandosketch.camgrid.data

import io.github.vandosketch.camgrid.core.SignalingException
import io.github.vandosketch.camgrid.core.WebRtcSignaling
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.IOException

/**
 * WebRTC signalling over HTTP (WHEP style, as go2rtc's `/api/webrtc?src=...` and MediaMTX's
 * `/<path>/whep` serve it): POSTs the complete SDP offer and returns the SDP answer.
 */
class WhepClient(private val http: HttpClient) {

    /**
     * Throws [SignalingException] for a bad response and [IOException] for an invalid URL or a
     * network failure. The IOException's message is safe to show and log: "Invalid URL" or the
     * type of the underlying failure (engine messages can contain the URL and are dropped).
     */
    suspend fun exchange(url: String, offerSdp: String): String {
        val target = HttpTarget.parse(url) ?: throw IOException("Invalid URL")
        return try {
            http.preparePost {
                target.applyTo(this)
                header(HttpHeaders.Accept, WebRtcSignaling.OFFER_CONTENT_TYPE)
                setBody(TextContent(offerSdp, ContentType.parse(WebRtcSignaling.OFFER_CONTENT_TYPE)))
            }.execute { response ->
                val code = response.status.value
                val body = if (code in 200..299) {
                    readLimited(response.bodyAsChannel(), MAX_ANSWER_BYTES)
                        ?: throw SignalingException(SignalingException.Reason.BAD_ANSWER, "BAD_ANSWER")
                } else {
                    ""
                }
                WebRtcSignaling.parseAnswer(code, response.headers[HttpHeaders.ContentType], body)
            }
        } catch (e: SignalingException) {
            throw e
        } catch (e: CancellationException) {
            // Our own cancellation propagates; one from inside the engine (a timeout) is a network error.
            currentCoroutineContext().ensureActive()
            throw networkError(e)
        } catch (e: Exception) {
            throw networkError(e)
        }
    }

    // The type only: the original message may contain the URL (Ktor's timeout messages do).
    private fun networkError(e: Exception) = IOException(e::class.simpleName ?: "Exception", e)

    companion object {
        /** Longest answer accepted; a real one is a few KB. */
        const val MAX_ANSWER_BYTES = 256 * 1024
    }
}
