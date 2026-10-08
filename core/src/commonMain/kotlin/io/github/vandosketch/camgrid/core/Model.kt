package io.github.vandosketch.camgrid.core

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** How a camera's URLs are played. Stored by name in the config. */
@Serializable
enum class StreamType {
    /**
     * Played by the platform's media player (ExoPlayer on Android, FFmpeg on desktop, VLCKit on
     * iOS): rtsp:// or rtsps://, or an http(s) media URL such as HLS or go2rtc's MP4.
     */
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

/**
 * The whole app configuration. [views] are shown one after the other, each as one or more pages
 * (see [ViewPaging]); there is always at least one, and view ids are unique. Auto tiles take
 * cameras in the order of [cameras]. [go2rtcBaseUrl] is optional and only used to import streams
 * (for example http://192.0.2.10:1984).
 *
 * [source] is set while cameras and views come from a config URL (see [RemoteConfig]); they are
 * then the last copy that loaded and are not edited on the device. Not written when null.
 *
 * Version history: 1 had a single uniform `layout` {columns, rows}; 2 replaced it with [views].
 * [ConfigCodec] migrates older files.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CamGridConfig(
    val views: List<CamView> = listOf(CamView.uniform(DEFAULT_VIEW_ID, "", 2, 2)),
    val cameras: List<Camera> = emptyList(),
    val go2rtcBaseUrl: String = "",
    val version: Int = CURRENT_VERSION,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val source: ConfigSource? = null,
) {
    init {
        require(views.isNotEmpty()) { "At least one view is needed" }
        require(views.map { it.id }.toSet().size == views.size) { "View ids must be unique" }
    }

    companion object {
        const val CURRENT_VERSION = 2

        /** Id of the view a version 1 grid becomes. */
        const val DEFAULT_VIEW_ID = "main"
    }
}

/**
 * Where this device loads its cameras and views from: an http(s) [url] and an optional [token],
 * sent as `Authorization: Bearer <token>`. [toString] shows neither.
 */
@Serializable
data class ConfigSource(val url: String, val token: String = "") {
    override fun toString(): String = "ConfigSource"
}
