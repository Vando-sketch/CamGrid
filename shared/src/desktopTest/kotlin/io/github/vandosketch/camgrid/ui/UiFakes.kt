package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.platform.LiveStream
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.VideoPlatform
import io.github.vandosketch.camgrid.platform.VideoZoom

/** Streams that are always playing, drawing a grey box; remembers the last mute state. */
class FakeVideoPlatform : VideoPlatform {
    var lastMuted: Boolean? = null
        private set

    override val supportedTypes = setOf(StreamType.RTSP, StreamType.WEBRTC)

    @Composable
    override fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream =
        remember(url) {
            object : LiveStream {
                override var status: StreamStatus by mutableStateOf(StreamStatus.Playing)

                override fun setMuted(muted: Boolean) {
                    lastMuted = muted
                }

                override fun release() {}
            }
        }

    @Composable
    override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode) {
        Box(modifier.background(Color.DarkGray))
    }
}

/** [count] cameras "Cam 1".."Cam N" (ids cam1..camN) on a 2×2 view: four per page. */
fun testConfig(count: Int): CamGridConfig = CamGridConfig(
    views = listOf(CamView.uniform("main", "", 2, 2)),
    cameras = (1..count).map { Camera(id = "cam$it", name = "Cam $it", gridUrl = "rtsp://192.0.2.1:8554/cam$it") },
)

/**
 * Like [FakeVideoPlatform], for zoom and sound tests: offers zoom when [supportsZoom], its
 * streams report [hasAudio], and it remembers the last zoom drawn and mute state.
 */
class ZoomingFakeVideoPlatform(
    private val hasAudio: Boolean? = null,
    override val supportsZoom: Boolean = true,
) : VideoPlatform {
    private val base = FakeVideoPlatform()

    var lastZoom: VideoZoom = VideoZoom.None
        private set

    val lastMuted: Boolean? get() = base.lastMuted

    override val supportedTypes get() = base.supportedTypes

    @Composable
    override fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream {
        val stream = base.rememberLiveStream(url, type, label, audioEnabled)
        val audio = hasAudio
        return remember(stream) {
            object : LiveStream by stream {
                override val hasAudio: Boolean? = audio
            }
        }
    }

    @Composable
    override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode) {
        Surface(stream, modifier, fit, VideoZoom.None)
    }

    @Composable
    override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode, zoom: VideoZoom) {
        SideEffect { lastZoom = zoom }
        base.Surface(stream, modifier, fit)
    }
}
