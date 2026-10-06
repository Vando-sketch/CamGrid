package io.github.vandosketch.camgrid.data

import io.github.vandosketch.camgrid.core.SignalingException
import io.github.vandosketch.camgrid.core.WebRtcSignaling
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WebRTC signalling over HTTP (WHEP style, as go2rtc's `/api/webrtc?src=...` and MediaMTX's
 * `/<path>/whep` serve it): POSTs the complete SDP offer and returns the SDP answer.
 */
object WhepClient {
    private const val TIMEOUT_MS = 5_000

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
            // MalformedURLException, IllegalArgumentException (not absolute), or a non-HTTP scheme.
            throw IOException("Invalid URL")
        }
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", WebRtcSignaling.OFFER_CONTENT_TYPE)
            connection.setRequestProperty("Accept", WebRtcSignaling.OFFER_CONTENT_TYPE)
            connection.setBasicAuthFrom(uri)
            connection.outputStream.use { it.write(offerSdp.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = if (code in 200..299) readLimited(connection) else ""
            WebRtcSignaling.parseAnswer(code, connection.contentType, body)
        } finally {
            connection.disconnect()
        }
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
