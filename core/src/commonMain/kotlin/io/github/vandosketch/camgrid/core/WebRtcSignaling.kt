package io.github.vandosketch.camgrid.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Why WebRTC signalling failed. [code] is short and safe to show and log (for example
 * `HTTP_404` or `CODEC_H265`); the message never contains the response body or the URL.
 */
class SignalingException(val reason: Reason, val code: String) : Exception(code) {
    enum class Reason {
        HTTP_STATUS,
        BAD_ANSWER,

        /** The server cannot send the video in a codec of the offer; [code] starts with [StreamFailures.CODEC_PREFIX]. */
        CODEC,
    }
}

/**
 * The HTTP side of WHEP-style WebRTC signalling, as served by go2rtc (`/api/webrtc?src=...`)
 * and MediaMTX (`/<path>/whep`): the client POSTs its complete SDP offer, ICE candidates
 * included, as `application/sdp` and gets the complete SDP answer back. No trickle ICE.
 */
object WebRtcSignaling {
    const val OFFER_CONTENT_TYPE = "application/sdp"

    /** Longest error body worth reading for [errorCode]; go2rtc's are one line. */
    const val MAX_ERROR_BYTES = 4 * 1024

    /**
     * The SDP answer from a signalling response. Accepts status 200 or 201 with either a raw
     * SDP body (starting with `v=`) or go2rtc's JSON form `{"type":"answer","sdp":"..."}`.
     * Anything else throws [SignalingException]: another status with the [errorCode] of
     * [body] (for an error, pass at most [MAX_ERROR_BYTES] of its body, or ""), and an answer
     * with video sections none of which sends (port 0 or `a=inactive`) with
     * [StreamFailures.CODEC_UNSUPPORTED]. go2rtc answers that way when the camera's audio codec
     * matched the offer and its video codec did not: the connection would carry audio only.
     */
    fun parseAnswer(statusCode: Int, contentType: String?, body: String): String {
        if (statusCode != 200 && statusCode != 201) {
            val code = errorCode(statusCode, body)
            val reason = if (StreamFailures.isCodecRejection(code)) SignalingException.Reason.CODEC else SignalingException.Reason.HTTP_STATUS
            throw SignalingException(reason, code)
        }
        val trimmed = body.trimStart()
        val sdp = when {
            trimmed.startsWith("v=") -> trimmed
            trimmed.startsWith("{") || contentType.orEmpty().contains("json", ignoreCase = true) -> sdpFromJson(trimmed)
            else -> null
        }
        if (sdp == null || !sdp.startsWith("v=")) throw badAnswer()
        if (!sendsVideo(sdp)) throw SignalingException(SignalingException.Reason.CODEC, StreamFailures.CODEC_UNSUPPORTED)
        return sdp
    }

    /**
     * A short code for a signalling response with error [statusCode], from go2rtc's error text
     * in [body] where it says why, otherwise `HTTP_<status>`. Takes nothing over from [body]
     * but a plain codec name (up to 10 letters and digits): server errors can echo URLs.
     *
     * go2rtc 1.9 answers 500 with its error as plain text (internal/webrtc/server.go):
     * `streams: codecs not matched: video:H265, audio:PCMA => video:H264` when the camera's
     * codecs (left of `=>`) and the offer's have none in common, which becomes `CODEC_H265`
     * (its first video codec, else [StreamFailures.CODEC_UNSUPPORTED]); `streams: <error>` when
     * it cannot get the stream from the camera, which becomes `SOURCE_TIMEOUT`,
     * `SOURCE_REFUSED`, `SOURCE_UNAUTHORIZED` or `SOURCE_FAILED`.
     */
    fun errorCode(statusCode: Int, body: String): String {
        val text = body.trim()
        val lower = text.lowercase()
        val mismatch = lower.indexOf(CODECS_NOT_MATCHED)
        return when {
            mismatch >= 0 -> codecCode(text.substring(mismatch + CODECS_NOT_MATCHED.length))
            statusCode >= 500 && lower.startsWith("streams:") -> when {
                "timeout" in lower -> "SOURCE_TIMEOUT"
                "refused" in lower -> "SOURCE_REFUSED"
                "401" in lower || "unauthorized" in lower -> "SOURCE_UNAUTHORIZED"
                else -> "SOURCE_FAILED"
            }
            else -> "HTTP_$statusCode"
        }
    }

    private const val CODECS_NOT_MATCHED = "codecs not matched"

    private val CODEC_NAME = Regex("[A-Za-z0-9]{1,10}")

    /** `CODEC_<the camera's first video codec>` from the text after "codecs not matched". */
    private fun codecCode(rest: String): String {
        val name = rest.removePrefix(":").substringBefore("=>")
            .split(',')
            .map { it.trim() }
            .firstOrNull { it.startsWith("video:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
        return if (name != null && CODEC_NAME.matches(name)) {
            StreamFailures.CODEC_PREFIX + name.uppercase()
        } else {
            StreamFailures.CODEC_UNSUPPORTED
        }
    }

    /**
     * False when [sdp] has video sections and none of them sends: each has port 0, or is
     * `a=inactive` (in the section, or at session level without a direction in the section).
     */
    private fun sendsVideo(sdp: String): Boolean {
        var sessionInactive = false
        var inSection = false
        var video = false
        var rejected = false
        var inactive = false
        var videoSections = 0
        var sendingSections = 0
        fun endSection() {
            if (!video) return
            videoSections++
            if (!rejected && !inactive) sendingSections++
        }
        for (raw in sdp.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("m=") -> {
                    endSection()
                    inSection = true
                    val fields = line.removePrefix("m=").split(' ')
                    video = fields.firstOrNull() == "video"
                    rejected = fields.getOrNull(1) == "0"
                    inactive = sessionInactive
                }
                line == "a=inactive" -> if (inSection) inactive = true else sessionInactive = true
                inSection && line in SENDING_DIRECTIONS -> inactive = false
            }
        }
        endSection()
        return videoSections == 0 || sendingSections > 0
    }

    private val SENDING_DIRECTIONS = setOf("a=sendonly", "a=sendrecv")

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
