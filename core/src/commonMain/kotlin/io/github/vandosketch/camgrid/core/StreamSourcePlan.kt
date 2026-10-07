package io.github.vandosketch.camgrid.core

/**
 * Which source one camera stream plays, and what it switches to when a failure says the
 * device can never play the current one, so the user sees a picture instead of a stream that
 * retries forever:
 *
 * - A WebRTC stream that go2rtc rejects for its codec ([StreamFailures.isCodecRejection],
 *   typically H.265, issue #17) switches to the same go2rtc stream as HTTP MP4
 *   ([Go2rtc.mp4StreamUrl]), played by the platform's other player (type [StreamType.RTSP]:
 *   FFmpeg, ExoPlayer, VLCKit). Not for other WHEP servers, which have no such URL.
 * - A stream the device cannot decode ([StreamFailures.isDecoderFailure], typically a high
 *   resolution or portrait main stream on a Fire TV, issue #16), or one rejected for its codec
 *   that has no MP4 form, switches to [lowerResolutionUrl], the camera's grid stream, when there
 *   is one that differs from the camera URL. That stream may then switch to MP4 itself. A
 *   decoder failure that may pass ([StreamFailures.isDefiniteDecoderFailure] false, such as a
 *   decoder that could not start while the grid's decoders were still being freed) switches
 *   only when it happens twice in a row without the stream playing in between ([onPlaying]).
 *
 * Each step happens at most once; every other failure retries the current source (the players
 * do that with backoff). A plan never returns to an earlier source: a new one is made when the
 * stream is opened again. Not thread-safe.
 *
 * @param audio whether the stream plays sound (fullscreen), which the MP4 URL asks go2rtc for.
 */
class StreamSourcePlan(url: String, private val type: StreamType, private val audio: Boolean, lowerResolutionUrl: String? = null) {

    /**
     * One source: [url] played as [type]. [viaMp4] for go2rtc's MP4 of a WebRTC stream whose
     * video [codec] (when go2rtc named it) WebRTC could not carry; [lowerResolution] for the grid
     * stream in place of the camera's fullscreen stream. [toString] never shows the URL.
     */
    data class Source(
        val url: String,
        val type: StreamType,
        val viaMp4: Boolean = false,
        val lowerResolution: Boolean = false,
        val codec: String? = null,
    ) {
        /** Whether this is not the stream the camera is configured with, so the user should be told. */
        val isFallback: Boolean get() = viaMp4 || lowerResolution

        override fun toString(): String =
            (if (lowerResolution) "lower resolution " else "") +
                if (viaMp4) "MP4" + codec?.let { " ($it)" }.orEmpty() else type.name
    }

    private val lower = lowerResolutionUrl?.trim()?.takeIf { it.isNotEmpty() && it != url.trim() }

    var current: Source = Source(url.trim(), type)
        private set

    // Decoder failures of the current source in a row, without playing in between.
    private var decoderFailures = 0

    /** Records that the current source plays. */
    fun onPlaying() {
        decoderFailures = 0
    }

    /** Records that the current source failed with [reason]; returns the new [current], or null to retry it. */
    fun onFailure(reason: String): Source? {
        val decoderFailure = StreamFailures.isDecoderFailure(reason)
        decoderFailures = if (decoderFailure) decoderFailures + 1 else 0
        val next = when {
            StreamFailures.isCodecRejection(reason) && current.type == StreamType.WEBRTC && !current.viaMp4 ->
                mp4Of(current, reason) ?: lowerResolution()
            decoderFailure && (StreamFailures.isDefiniteDecoderFailure(reason) || decoderFailures >= 2) -> lowerResolution()
            else -> null
        } ?: return null
        current = next
        decoderFailures = 0
        return next
    }

    private fun mp4Of(source: Source, reason: String): Source? {
        val mp4 = Go2rtc.mp4StreamUrl(source.url, audio) ?: return null
        return source.copy(url = mp4, type = StreamType.RTSP, viaMp4 = true, codec = StreamFailures.codecOf(reason))
    }

    private fun lowerResolution(): Source? {
        if (current.lowerResolution) return null
        // The grid stream is played with the camera's stream type, like the fullscreen stream.
        return Source(lower ?: return null, type, lowerResolution = true)
    }
}
