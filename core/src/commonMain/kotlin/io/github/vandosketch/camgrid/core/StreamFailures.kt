package io.github.vandosketch.camgrid.core

/**
 * The failure codes the players report that [StreamSourcePlan] acts on. Every code is short and
 * safe to show on a tile and to log: it never contains a URL or a server's error text.
 */
object StreamFailures {
    /**
     * The server has the stream only in a video codec the player did not offer, followed by
     * that codec as go2rtc names it: `CODEC_H265`. go2rtc answers such a WebRTC offer with
     * HTTP 500 "codecs not matched", or, when the audio codec matched, with the video inactive.
     */
    const val CODEC_PREFIX = "CODEC_"

    /** A codec rejection where the server did not name the codec. */
    const val CODEC_UNSUPPORTED = "CODEC_UNSUPPORTED"

    /** The video decoder was set up but produced no frame before the watchdog's timeout. */
    const val DECODER_NO_OUTPUT = "DECODER_NO_OUTPUT"

    /** A video decoder reported errors while the stream showed no frame. */
    const val DECODER_ERROR = "DECODER_ERROR"

    fun isCodecRejection(code: String): Boolean = code.startsWith(CODEC_PREFIX)

    /** The codec named by a codec rejection (`H265`), or null. */
    fun codecOf(code: String): String? =
        code.takeIf { isCodecRejection(it) && it != CODEC_UNSUPPORTED }?.removePrefix(CODEC_PREFIX)

    /**
     * The device cannot decode the stream, typically a resolution beyond its hardware decoder
     * (a portrait 1536x2048 or 4K main stream on a 1080p Fire TV decoder): Media3's `DECODER_*`
     * and `DECODING_*` codes (`DECODING_FORMAT_EXCEEDS_CAPABILITIES`, `DECODER_INIT_FAILED`, ...),
     * [DECODER_NO_OUTPUT], [DECODER_ERROR] and FFmpeg's `NO_DECODER`. Media3's
     * `DECODING_RESOURCES_RECLAIMED` is not one: the system took the decoder for another app.
     */
    fun isDecoderFailure(code: String): Boolean =
        (code.startsWith("DECODER_") || code.startsWith("DECODING_") || code == "NO_DECODER") &&
            code != "DECODING_RESOURCES_RECLAIMED"

    /**
     * A [isDecoderFailure] that retrying cannot fix: the decoder said the format is beyond it,
     * or it got the stream for the watchdog's whole connect timeout and showed nothing. Others,
     * like `DECODER_INIT_FAILED`, also happen while the decoders of the screen just left are
     * still being freed.
     */
    fun isDefiniteDecoderFailure(code: String): Boolean = code in DEFINITE_DECODER_FAILURES

    private val DEFINITE_DECODER_FAILURES = setOf(
        "DECODING_FORMAT_EXCEEDS_CAPABILITIES",
        "DECODING_FORMAT_UNSUPPORTED",
        DECODER_NO_OUTPUT,
        DECODER_ERROR,
    )
}
