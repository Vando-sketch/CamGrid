package io.github.vandosketch.camgrid.core

import kotlinx.serialization.Serializable

/** How a camera's URLs are played. Stored by name in the config. */
@Serializable
enum class StreamType {
    /** Played by ExoPlayer: rtsp:// or rtsps://, or an http(s) media URL such as HLS. */
    RTSP,

    /**
     * WebRTC with WHEP-style signalling: the app POSTs an SDP offer (application/sdp) to the
     * http(s) URL and gets the SDP answer back. go2rtc serves this at `/api/webrtc?src=<name>`.
     */
    WEBRTC,
}

/**
 * One camera on the wall.
 *
 * [gridUrl] is the lower-resolution stream played in a grid tile. [detailUrl] is the
 * higher-resolution stream played in fullscreen; when it is blank, fullscreen falls back
 * to [gridUrl]. Both URLs are played as [streamType]. URLs may carry credentials
 * (rtsp://user:pass@host/...), so never log them unredacted (see [UrlRedactor]).
 */
@Serializable
data class Camera(
    val id: String,
    val name: String,
    val gridUrl: String,
    val detailUrl: String = "",
    val streamType: StreamType = StreamType.RTSP,
) {
    /** The URL to play in fullscreen: [detailUrl] if set, otherwise [gridUrl]. Trimmed. */
    val fullscreenUrl: String
        get() = detailUrl.trim().ifEmpty { gridUrl.trim() }
}

/** Grid dimensions. Both values must be within [MIN_SIZE]..[MAX_SIZE], otherwise [IllegalArgumentException]. */
@Serializable
data class GridLayout(
    val columns: Int = 2,
    val rows: Int = 2,
) {
    init {
        require(columns in MIN_SIZE..MAX_SIZE) { "columns must be in $MIN_SIZE..$MAX_SIZE, was $columns" }
        require(rows in MIN_SIZE..MAX_SIZE) { "rows must be in $MIN_SIZE..$MAX_SIZE, was $rows" }
    }

    /** Number of tiles on one page: columns * rows. */
    val tilesPerPage: Int
        get() = columns * rows

    companion object {
        const val MIN_SIZE = 1
        const val MAX_SIZE = 4
    }
}

/**
 * The whole app configuration. Camera order in the grid is the order of [cameras].
 * [go2rtcBaseUrl] is optional and only used to import streams (for example http://192.0.2.10:1984).
 */
@Serializable
data class CamGridConfig(
    val layout: GridLayout = GridLayout(),
    val cameras: List<Camera> = emptyList(),
    val go2rtcBaseUrl: String = "",
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
