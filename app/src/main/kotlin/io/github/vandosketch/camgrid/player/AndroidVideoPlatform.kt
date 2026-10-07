package io.github.vandosketch.camgrid.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.data.CamGridHttp
import io.github.vandosketch.camgrid.data.WhepClient
import io.github.vandosketch.camgrid.platform.FallbackSurface
import io.github.vandosketch.camgrid.platform.LiveStream
import io.github.vandosketch.camgrid.platform.VideoPlatform
import io.github.vandosketch.camgrid.platform.VideoZoom
import io.github.vandosketch.camgrid.platform.rememberStreamWithFallback

/**
 * Android and Fire TV players: [StreamPlayer] (ExoPlayer) for [StreamType.RTSP] and
 * [WebRtcStream] (libwebrtc) for [StreamType.WEBRTC].
 */
object AndroidVideoPlatform : VideoPlatform {

    override val supportedTypes: Set<StreamType> = setOf(StreamType.RTSP, StreamType.WEBRTC)

    private val whep by lazy { WhepClient(CamGridHttp.client) }

    @Composable
    override fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream? =
        rememberLiveStream(url, type, label, audioEnabled, lowerResolutionUrl = null)

    /**
     * A [LiveStream] for [url] that exists only while the lifecycle is STARTED: it is released
     * when the activity stops (screen off) or the composable leaves the composition, and
     * recreated on the next start. Returns null while there is no stream. A go2rtc WebRTC
     * stream in a codec libwebrtc does not offer plays as go2rtc's MP4 in ExoPlayer, and a
     * stream the device cannot decode as [lowerResolutionUrl] ([rememberStreamWithFallback]).
     */
    @Composable
    override fun rememberLiveStream(
        url: String,
        type: StreamType,
        label: String,
        audioEnabled: Boolean,
        lowerResolutionUrl: String?,
    ): LiveStream? = rememberStreamWithFallback(url, type, label, audioEnabled, lowerResolutionUrl) { sourceUrl, sourceType ->
        rememberSourceStream(sourceUrl, sourceType, label, audioEnabled)
    }

    @Composable
    private fun rememberSourceStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream? {
        val context = LocalContext.current.applicationContext
        var stream by remember { mutableStateOf<LiveStream?>(null) }
        LifecycleStartEffect(url, type, audioEnabled) {
            val created = when (type) {
                StreamType.RTSP -> StreamPlayer(context, url, label, audioEnabled)
                StreamType.WEBRTC -> WebRtcStream(context, whep, url, label, audioEnabled)
            }
            stream = created
            onStopOrDispose {
                stream = null
                created.release()
            }
        }
        return stream
    }

    /**
     * Zoomed video is drawn into a TextureView (ExoPlayer's, or [WebRtcTextureRenderer] for
     * WebRTC), which Compose clips like any content; unzoomed video keeps its SurfaceView.
     */
    override val supportsZoom: Boolean get() = true

    /** Renders [stream] centred in [modifier]'s bounds, letterboxed or cropped per [fit]; black while it is null. */
    @Composable
    override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode) {
        Surface(stream, modifier, fit, VideoZoom.None)
    }

    @Composable
    override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode, zoom: VideoZoom) {
        FallbackSurface(stream, modifier) { own, videoModifier ->
            when (own) {
                is WebRtcStream -> WebRtcSurface(own, videoModifier, fit, zoom)
                is StreamPlayer -> VideoSurface(own.player, videoModifier, fit, zoom)
                else -> VideoSurface(null, videoModifier, fit)
            }
        }
    }
}
