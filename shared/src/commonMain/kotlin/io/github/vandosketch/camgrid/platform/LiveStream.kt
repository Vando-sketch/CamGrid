package io.github.vandosketch.camgrid.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.StreamType

/** What a stream is doing, shown on its tile or in fullscreen. */
sealed interface StreamStatus {
    data object Connecting : StreamStatus
    data object Playing : StreamStatus

    /** Playback failed or ended; the next attempt starts in [retryInSeconds]. */
    data class Offline(val reason: String, val retryInSeconds: Int) : StreamStatus
}

/** One live stream that keeps reconnecting until [release]. Main thread only. */
interface LiveStream {
    /** Compose snapshot state, so composables reading it recompose on change. */
    val status: StreamStatus

    /** Only has an effect on a stream created with audio enabled. */
    fun setMuted(muted: Boolean)

    fun release()
}

/**
 * The platform's video players. Android/Fire TV: ExoPlayer for RTSP and libwebrtc for WebRTC.
 * iOS: VLCKit for RTSP and Google's WebRTC framework for WebRTC (`IosVideoPlatform`).
 * Desktop: FFmpeg (LGPL build) for RTSP and webrtc-java for WebRTC (`DesktopVideoPlatform`).
 */
interface VideoPlatform {
    /** Stream types this platform plays natively. */
    val supportedTypes: Set<StreamType>

    /**
     * A [LiveStream] for [url] that exists only while the app is in the foreground and the
     * composable is in the composition; null while there is none. The platforms switch a
     * go2rtc WebRTC stream whose codec WebRTC cannot carry to go2rtc's MP4 of it
     * ([rememberStreamWithFallback]).
     *
     * @param label used in log messages instead of the URL (the URL may contain credentials).
     * @param audioEnabled false for grid tiles: no audio is received or decoded at all.
     */
    @Composable
    fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream?

    /**
     * Like the other [rememberLiveStream], and when the device cannot decode [url] (for a
     * fullscreen stream beyond its decoder, issue #16) it plays [lowerResolutionUrl], the
     * camera's grid stream, instead, with a note on the video. A platform without fallbacks
     * plays [url] only.
     */
    @Composable
    fun rememberLiveStream(
        url: String,
        type: StreamType,
        label: String,
        audioEnabled: Boolean,
        lowerResolutionUrl: String?,
    ): LiveStream? = rememberLiveStream(url, type, label, audioEnabled)

    /**
     * Renders [stream] centred in [modifier]'s bounds, letterboxed or cropped per [fit]; black
     * while null. A stream playing a fallback source shows a small note saying so
     * ([FallbackSurface]).
     */
    @Composable
    fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode)
}
