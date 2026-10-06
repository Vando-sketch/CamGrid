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
 * Desktop and iOS: WebRTC only; RTSP cameras are played through go2rtc's WebRTC endpoint
 * (see `PlaybackSource`).
 */
interface VideoPlatform {
    /** Stream types this platform plays natively. */
    val supportedTypes: Set<StreamType>

    /**
     * A [LiveStream] for [url] that exists only while the app is in the foreground and the
     * composable is in the composition; null while there is none.
     *
     * @param label used in log messages instead of the URL (the URL may contain credentials).
     * @param audioEnabled false for grid tiles: no audio is received or decoded at all.
     */
    @Composable
    fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream?

    /** Renders [stream] centred in [modifier]'s bounds, letterboxed or cropped per [fit]; black while null. */
    @Composable
    fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode)
}
