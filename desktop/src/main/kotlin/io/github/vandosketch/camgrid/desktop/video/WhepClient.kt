package io.github.vandosketch.camgrid.desktop.video

import io.github.vandosketch.camgrid.core.SignalingException
import io.github.vandosketch.camgrid.core.WebRtcSignaling
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * WebRTC signalling over HTTP (WHEP style, as go2rtc's `/api/webrtc?src=...` and MediaMTX's
 * `/<path>/whep` serve it): POSTs the complete SDP offer and returns the SDP answer. The
 * desktop twin of the Android app's `WhepClient`.
 */
object WhepClient {
    private const val CONNECT_TIMEOUT_MS = 5_000

    /**
     * go2rtc answers only after gathering its own ICE candidates, which waits up to 5 s for its
     * STUN server (stun.l.google.com by default) when the server has no internet access.
     */
    private const val READ_TIMEOUT_MS = 12_000

    /** Longest answer accepted; a real one is a few KB. */
    private const val MAX_ANSWER_CHARS = 256 * 1024

    /**
     * Throws [SignalingException] for a bad response and [IOException] for network failures,
     * whose messages may contain the host, so log only their type. Runs on [Dispatchers.IO].
     */
    suspend fun exchange(url: String, offerSdp: String): String = withContext(Dispatchers.IO) {
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            throw IOException("Invalid URL")
        }
        val connection = try {
            uri.toURL().openConnection() as HttpURLConnection
        } catch (_: Exception) {
            throw IOException("Invalid URL")
        }
        // Blocking socket IO ignores cancellation; disconnecting makes it throw at once.
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { connection.disconnect() }
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", WebRtcSignaling.OFFER_CONTENT_TYPE)
            connection.setRequestProperty("Accept", WebRtcSignaling.OFFER_CONTENT_TYPE)
            uri.userInfo?.let { userInfo ->
                val token = Base64.getEncoder().encodeToString(userInfo.toByteArray(Charsets.UTF_8))
                connection.setRequestProperty("Authorization", "Basic $token")
            }
            connection.outputStream.use { it.write(offerSdp.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            // For an error, go2rtc says why in the body ("codecs not matched"); WebRtcSignaling turns it into a code.
            val body = if (code in 200..299) readLimited(connection) else errorBody(connection)
            WebRtcSignaling.parseAnswer(code, connection.contentType, body)
        } finally {
            cancelHandle?.dispose()
            connection.disconnect()
        }
    }

    /** At most [WebRtcSignaling.MAX_ERROR_BYTES] of an error response, or "" when it is longer or unreadable. */
    private fun errorBody(connection: HttpURLConnection): String = try {
        connection.errorStream?.use { stream ->
            val bytes = stream.readNBytes(WebRtcSignaling.MAX_ERROR_BYTES + 1)
            if (bytes.size > WebRtcSignaling.MAX_ERROR_BYTES) "" else bytes.decodeToString()
        }.orEmpty()
    } catch (_: IOException) {
        ""
    }

    private fun readLimited(connection: HttpURLConnection): String =
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(MAX_ANSWER_CHARS + 1)
            var length = 0
            while (length < buffer.size) {
                val read = reader.read(buffer, length, buffer.size - length)
                if (read < 0) break
                length += read
            }
            if (length > MAX_ANSWER_CHARS) {
                throw SignalingException(SignalingException.Reason.BAD_ANSWER, "BAD_ANSWER")
            }
            String(buffer, 0, length)
        }
}
