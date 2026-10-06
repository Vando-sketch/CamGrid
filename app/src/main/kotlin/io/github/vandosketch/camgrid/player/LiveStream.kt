package io.github.vandosketch.camgrid.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.vandosketch.camgrid.core.StreamType

/** What a stream is doing, shown on its tile or in fullscreen. */
sealed interface StreamStatus {
    data object Connecting : StreamStatus
    data object Playing : StreamStatus

    /** Playback failed or ended; the next attempt starts in [retryInSeconds]. */
    data class Offline(val reason: String, val retryInSeconds: Int) : StreamStatus
}

/**
 * One live stream that keeps reconnecting until [release]: [StreamPlayer] (ExoPlayer, for
 * [StreamType.RTSP]) or [WebRtcStream] (for [StreamType.WEBRTC]). Main thread only.
 */
interface LiveStream {
    /** Compose snapshot state, so composables reading it recompose on change. */
    val status: StreamStatus

    /** Only has an effect on a stream created with audio enabled. */
    fun setMuted(muted: Boolean)

    fun release()
}

/**
 * A [LiveStream] for [url] that exists only while the lifecycle is STARTED: it is released
 * when the activity stops (screen off) or the composable leaves the composition, and recreated
 * on the next start. Returns null while there is no stream.
 *
 * @param label used in log messages instead of the URL (the URL may contain credentials).
 * @param audioEnabled false for grid tiles: no audio is received or decoded at all.
 */
@Composable
fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream? {
    val context = LocalContext.current.applicationContext
    var stream by remember { mutableStateOf<LiveStream?>(null) }
    LifecycleStartEffect(url, type, audioEnabled) {
        val created = when (type) {
            StreamType.RTSP -> StreamPlayer(context, url, label, audioEnabled)
            StreamType.WEBRTC -> WebRtcStream(context, url, label, audioEnabled)
        }
        stream = created
        onStopOrDispose {
            stream = null
            created.release()
        }
    }
    return stream
}

/** Renders [stream] letterboxed and centred in [modifier]'s bounds; black while it is null. */
@Composable
fun LiveStreamSurface(stream: LiveStream?, modifier: Modifier = Modifier) {
    when (stream) {
        is WebRtcStream -> WebRtcSurface(stream, modifier)
        is StreamPlayer -> VideoSurface(stream.player, modifier)
        else -> VideoSurface(null, modifier)
    }
}
