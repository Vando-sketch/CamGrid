package io.github.vandosketch.camgrid.desktop.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.LiveStream
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.VideoPlatform
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Desktop players: [WebRtcConnection] (webrtc-java) for [StreamType.WEBRTC] and
 * [RtspConnection] (FFmpeg) for [StreamType.RTSP], both drawn by Compose itself (no native
 * view), so overlays, clipping and animations work on top of the video.
 */
object DesktopVideoPlatform : VideoPlatform {

    override val supportedTypes: Set<StreamType> = setOf(StreamType.RTSP, StreamType.WEBRTC)

    /** Status changes run on the UI thread (Swing). */
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * A stream that exists while the window is started (not minimised) and the composable is
     * in the composition; recreated when the window comes back.
     */
    @Composable
    override fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream? {
        var stream by remember { mutableStateOf<DesktopLiveStream?>(null) }
        LifecycleStartEffect(url, type, audioEnabled) {
            val created = DesktopLiveStream(uiScope, url, type, label, audioEnabled)
            stream = created
            onStopOrDispose {
                stream = null
                created.release()
            }
        }
        return stream
    }

    @Composable
    override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode) {
        VideoCanvas((stream as? DesktopLiveStream)?.frames, modifier, fit)
    }
}

/** One camera: a [StreamRunner] with the transport for [type], drawing into [frames]. */
class DesktopLiveStream(
    scope: CoroutineScope,
    url: String,
    type: StreamType,
    label: String,
    audioEnabled: Boolean,
) : LiveStream {
    val frames = FrameHolder()

    private val runner = StreamRunner(
        scope = scope,
        label = label,
        connect = {
            // Black while reconnecting rather than a frozen picture that looks live.
            frames.clear()
            when (type) {
                StreamType.RTSP -> RtspConnection(url, frames, audioEnabled)
                StreamType.WEBRTC -> WebRtcConnection(url, frames, audioEnabled)
            }
        },
        log = { AppLog.w(it) },
    )

    init {
        runner.start()
    }

    override val status: StreamStatus get() = runner.status

    override fun setMuted(muted: Boolean) = runner.setMuted(muted)

    override fun release() = runner.release()
}

/**
 * Draws the latest frame of [frames] centred in the bounds, letterboxed or cropped per [fit];
 * black without a frame. Tells the decoder the drawn size, so it converts no more pixels than
 * are shown.
 */
@Composable
fun VideoCanvas(frames: FrameHolder?, modifier: Modifier, fit: FitMode) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    SideEffect { frames?.viewport = FrameHolder.Viewport(box.width, box.height, fit) }
    val cache = remember(frames) { FrameImageCache() }
    DisposableEffect(cache) { onDispose(cache::clear) }
    Canvas(modifier.background(Color.Black).onSizeChanged { box = it }) {
        val holder = frames ?: return@Canvas
        // Reading the version makes Compose redraw (only redraw, no recomposition) per frame.
        val version = holder.version.value
        val image = cache.image(holder, version) ?: return@Canvas
        val r = FrameGeometry.place(image.width, image.height, size.width, size.height, fit) ?: return@Canvas
        drawImage(
            image = image,
            srcOffset = IntOffset(r.srcLeft, r.srcTop),
            srcSize = IntSize(r.srcWidth, r.srcHeight),
            dstOffset = IntOffset(r.dstLeft.roundToInt(), r.dstTop.roundToInt()),
            dstSize = IntSize(r.dstWidth.roundToInt(), r.dstHeight.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** The frame as a Skia image, converted once per new frame (not per draw). UI thread only. */
private class FrameImageCache {
    private var version = Long.MIN_VALUE
    private var image: ImageBitmap? = null

    fun image(holder: FrameHolder, current: Long): ImageBitmap? {
        if (current != version) {
            version = current
            image = holder.withLatest { frame ->
                val info = ImageInfo(frame.width, frame.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                // makeRaster copies the pixels, so the decoder can reuse its buffer right away.
                Image.makeRaster(info, frame.pixels, frame.width * 4).toComposeImageBitmap()
            }
        }
        return image
    }

    fun clear() {
        image = null
    }
}
