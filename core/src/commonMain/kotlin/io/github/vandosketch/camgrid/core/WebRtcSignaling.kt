package io.github.vandosketch.camgrid.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Why WebRTC signalling failed. [code] is short and safe to show and log (for example
 * `HTTP_404`); the message never contains the response body or the URL.
 */
class SignalingException(val reason: Reason, val code: String) : Exception(code) {
    enum class Reason { HTTP_STATUS, BAD_ANSWER }
}

/**
 * The HTTP side of WHEP-style WebRTC signalling, as served by go2rtc (`/api/webrtc?src=...`)
 * and MediaMTX (`/<path>/whep`): the client POSTs its complete SDP offer, ICE candidates
 * included, as `application/sdp` and gets the complete SDP answer back. No trickle ICE.
 */
object WebRtcSignaling {
    const val OFFER_CONTENT_TYPE = "application/sdp"

    /**
     * The SDP answer from a signalling response. Accepts status 200 or 201 with either a raw
     * SDP body (starting with `v=`) or go2rtc's JSON form `{"type":"answer","sdp":"..."}`.
     * Anything else throws [SignalingException].
     */
    fun parseAnswer(statusCode: Int, contentType: String?, body: String): String {
        if (statusCode != 200 && statusCode != 201) {
            throw SignalingException(SignalingException.Reason.HTTP_STATUS, "HTTP_$statusCode")
        }
        val trimmed = body.trimStart()
        val sdp = when {
            trimmed.startsWith("v=") -> trimmed
            trimmed.startsWith("{") || contentType.orEmpty().contains("json", ignoreCase = true) -> sdpFromJson(trimmed)
            else -> null
        }
        if (sdp == null || !sdp.startsWith("v=")) throw badAnswer()
        return sdp
    }

    private fun sdpFromJson(body: String): String? {
        val element = try {
            Json.parseToJsonElement(body)
        } catch (_: SerializationException) {
            return null
        }
        val obj = element as? JsonObject ?: return null
        val type = (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (type != null && type != "answer") return null
        return (obj["sdp"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trimStart()
    }

    private fun badAnswer() = SignalingException(SignalingException.Reason.BAD_ANSWER, "BAD_ANSWER")
}
