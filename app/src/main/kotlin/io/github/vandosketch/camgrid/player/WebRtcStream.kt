package io.github.vandosketch.camgrid.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.Go2rtc
import io.github.vandosketch.camgrid.core.ReconnectPolicy
import io.github.vandosketch.camgrid.core.SignalingException
import io.github.vandosketch.camgrid.core.StreamFailures
import io.github.vandosketch.camgrid.core.StreamWatchdog
import io.github.vandosketch.camgrid.data.WhepClient
import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.LiveStream
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.VideoZoom
import io.github.vandosketch.camgrid.platform.zoomed
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RendererCommon
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

/**
 * One receive-only WebRTC connection to a WHEP-style endpoint such as go2rtc's
 * `/api/webrtc?src=<name>`, reconnecting with [ReconnectPolicy] backoff when signalling fails,
 * ICE fails, or [StreamWatchdog] sees no frames. Main thread only; call [release] when done.
 *
 * @param label used in log messages instead of the URL (the URL may contain credentials).
 * @param audioEnabled false for grid tiles: only video is negotiated, so the server sends no
 *   audio at all. With true, [setMuted] toggles the volume.
 */
class WebRtcStream(
    context: Context,
    private val whep: WhepClient,
    url: String,
    private val label: String,
    private val audioEnabled: Boolean,
) : LiveStream {

    // Configs saved before the editor normalised URLs may hold a go2rtc player page URL.
    private val url = Go2rtc.webrtcEndpoint(url) ?: url

    override var status by mutableStateOf<StreamStatus>(StreamStatus.Connecting)
        private set

    private val engine = WebRtcEngine.get(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private val reconnectPolicy = ReconnectPolicy()
    private val watchdog = StreamWatchdog()
    private val frames = FrameForwarder()
    private var attempt = 0
    private var muted = false
    private var released = false
    // Read by the frame forwarder on a decoder thread.
    @Volatile private var session: Session? = null
    private var job: Job? = null

    init {
        connect()
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        session?.applyVolume()
    }

    override fun release() {
        if (released) return
        released = true
        scope.cancel()
        frames.target = null
        session?.close()
        session = null
    }

    /** Where decoded frames go; [renderer] null (or another renderer detaching) stops it. */
    internal fun attachRenderer(renderer: VideoSink) {
        frames.target = renderer
    }

    internal fun detachRenderer(renderer: VideoSink) {
        if (frames.target === renderer) frames.target = null
    }

    private fun connect() {
        val current = Session()
        session = current
        frames.reset()
        status = StreamStatus.Connecting
        val startedAt = SystemClock.elapsedRealtime()
        job = scope.launch {
            try {
                current.negotiate()
                while (true) {
                    delay(WATCHDOG_INTERVAL_MS)
                    val lastFrame = frames.lastFrameAtMillis.takeIf { it != 0L }
                    when (watchdog.check(SystemClock.elapsedRealtime(), startedAt, lastFrame)) {
                        StreamWatchdog.Verdict.OK -> Unit
                        StreamWatchdog.Verdict.CONNECT_TIMEOUT -> throw WebRtcFailure(noFrameReason(startedAt))
                        StreamWatchdog.Verdict.STALLED -> {
                            logDecoder("STALLED", startedAt)
                            throw WebRtcFailure("STALLED")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignalingException) {
                fail(current, e.code)
            } catch (e: WebRtcFailure) {
                fail(current, e.code)
            } catch (e: IOException) {
                // WhepClient's IOException messages are safe: "Invalid URL" or the failure's type.
                fail(current, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Why no frame came since [startedAt]: [StreamFailures.DECODER_ERROR] when a video decoder
     * failed meanwhile (the stream arrived, the device could not decode it; fullscreen then
     * switches to the grid stream), otherwise TIMEOUT.
     */
    private fun noFrameReason(startedAt: Long): String {
        val decoderFailed = WebRtcDecoderReports.lastError?.let { it.atMillis >= startedAt } == true
        val reason = if (decoderFailed) StreamFailures.DECODER_ERROR else "TIMEOUT"
        logDecoder(reason, startedAt)
        return reason
    }

    /** Logs the decoders' last reports since [startedAt], for a bug report: codec, size, decoder, never the URL. */
    private fun logDecoder(reason: String, startedAt: Long) {
        val setup = WebRtcDecoderReports.lastSetup?.takeIf { it.atMillis >= startedAt }
        val error = WebRtcDecoderReports.lastError?.takeIf { it.atMillis >= startedAt }
        AppLog.w("WebRTC stream '$label' $reason: decoder set up ${setup ?: "never"}; last error ${error ?: "none"}")
    }

    private fun onFirstFrame(from: Session) {
        if (released || session !== from) return
        attempt = 0
        status = StreamStatus.Playing
    }

    /** Tears down [from] (if it is still current) and reconnects after the backoff delay. */
    private fun fail(from: Session, reason: String) {
        if (released || session !== from) return
        AppLog.w("WebRTC stream '$label' failed: $reason (attempt $attempt)")
        job?.cancel()
        from.close()
        session = null
        val delayMillis = reconnectPolicy.delayMillis(attempt)
        attempt++
        job = scope.launch {
            // Count down in whole seconds so the tile can show "retrying in N s".
            var remaining = delayMillis
            while (remaining > 0) {
                status = StreamStatus.Offline(reason = reason, retryInSeconds = ((remaining + 999) / 1000).toInt())
                val step = minOf(remaining, 1_000L)
                delay(step)
                remaining -= step
            }
            connect()
        }
    }

    /** One peer connection; replaced by a new one on every reconnect. */
    private inner class Session {
        private val gatheringComplete = CompletableDeferred<Unit>()
        private var audioTrack: AudioTrack? = null
        private var closed = false

        private val observer = object : PeerConnection.Observer {
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) gatheringComplete.complete(Unit)
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                // DISCONNECTED often recovers by itself; the watchdog catches it if it does not.
                if (state == PeerConnection.PeerConnectionState.FAILED) {
                    mainHandler.post { fail(this@Session, "ICE_FAILED") }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                // Called on the WebRTC signalling thread; track objects are thread-safe.
                when (val track = transceiver.receiver.track()) {
                    // PeerConnection.dispose() disposes the track, which removes the sink again.
                    is VideoTrack -> track.addSink(frames)
                    is AudioTrack -> mainHandler.post {
                        if (!closed) {
                            audioTrack = track
                            applyVolume()
                        }
                    }
                    else -> Unit
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceCandidate(candidate: IceCandidate) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
        }

        // No STUN or TURN servers: CamGrid is LAN only, where host candidates connect directly.
        private val peerConnection: PeerConnection? = engine.factory.createPeerConnection(
            PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            },
            observer,
        )

        init {
            val recvOnly = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, recvOnly)
            if (audioEnabled) peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, recvOnly)
        }

        /** Offer, wait for ICE candidates, POST, apply the answer. Frames follow on their own. */
        suspend fun negotiate() {
            val pc = peerConnection ?: throw WebRtcFailure("NO_PEER_CONNECTION")
            val offer = pc.awaitSdp("OFFER_FAILED") { createOffer(it, MediaConstraints()) }
            pc.awaitSet("OFFER_FAILED") { setLocalDescription(it, offer) }
            // The server does not take trickled candidates, so the offer must carry them all.
            // Host candidates on a LAN gather almost at once; send what there is after the timeout.
            withTimeoutOrNull(ICE_GATHERING_TIMEOUT_MS) { gatheringComplete.await() }
            val offerSdp = pc.localDescription?.description ?: offer.description
            val answerSdp = whep.exchange(url, offerSdp)
            if (closed) return
            pc.awaitSet("ANSWER_REJECTED") {
                setRemoteDescription(it, SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
            }
        }

        fun applyVolume() {
            audioTrack?.setVolume(if (muted) 0.0 else 1.0)
        }

        fun close() {
            if (closed) return
            closed = true
            // dispose() also closes the connection and frees the tracks and their sinks.
            peerConnection?.let(engine::disposeLater)
            audioTrack = null
        }

        private suspend fun PeerConnection.awaitSdp(
            failure: String,
            call: PeerConnection.(SdpObserver) -> Unit,
        ): SessionDescription = suspendCancellableCoroutine { continuation ->
            call(object : SdpObserverAdapter() {
                override fun onCreateSuccess(description: SessionDescription) {
                    if (continuation.isActive) continuation.resume(description)
                }

                override fun onCreateFailure(error: String?) {
                    if (continuation.isActive) continuation.resumeWithException(WebRtcFailure(failure))
                }
            })
        }

        private suspend fun PeerConnection.awaitSet(
            failure: String,
            call: PeerConnection.(SdpObserver) -> Unit,
        ): Unit = suspendCancellableCoroutine { continuation ->
            call(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    if (continuation.isActive) continuation.resumeWithException(WebRtcFailure(failure))
                }
            })
        }
    }

    /**
     * Forwards decoded frames (on a WebRTC decoder thread) to whichever renderer is attached,
     * and records when the last one arrived for the watchdog.
     */
    private inner class FrameForwarder : VideoSink {
        @Volatile var target: VideoSink? = null

        @Volatile var lastFrameAtMillis = 0L
            private set

        @Volatile private var firstFrameSeen = false

        fun reset() {
            lastFrameAtMillis = 0L
            firstFrameSeen = false
        }

        override fun onFrame(frame: VideoFrame) {
            lastFrameAtMillis = SystemClock.elapsedRealtime()
            target?.onFrame(frame)
            if (!firstFrameSeen) {
                firstFrameSeen = true
                val current = session
                mainHandler.post { current?.let(::onFirstFrame) }
            }
        }
    }

    private class WebRtcFailure(val code: String) : Exception(code)

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    private companion object {
        const val WATCHDOG_INTERVAL_MS = 1_000L
        const val ICE_GATHERING_TIMEOUT_MS = 2_000L
    }
}

/**
 * Renders [stream] centred in [modifier]'s bounds, letterboxed or cropped per [fit], like
 * [VideoSurface] does for ExoPlayer. For [FitMode.FIT] the renderer measures itself to the
 * video's aspect ratio within the loose constraints the Box gives it; for [FitMode.CROP] it
 * fills them and crops the frame itself while drawing, so nothing reaches past the bounds.
 * With a [zoom] (fullscreen, even at [VideoZoom.None]) it is the zoomable TextureView picture
 * from the start, so zooming never swaps views (issue #30).
 */
@Composable
fun WebRtcSurface(
    stream: WebRtcStream,
    modifier: Modifier = Modifier,
    fit: FitMode = FitMode.FIT,
    zoom: VideoZoom? = null,
) {
    if (zoom != null) {
        ZoomedWebRtcSurface(stream, modifier, zoom)
        return
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        // A new renderer per stream, so a reused view never keeps receiving an old stream.
        key(stream) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(WebRtcEngine.get(context).eglBase.eglBaseContext, null)
                        setScalingType(fit.scalingType())
                        // Lets the display hardware scale the frame instead of the GPU.
                        setEnableHardwareScaler(true)
                        stream.attachRenderer(this)
                    }
                },
                update = { renderer -> renderer.setScalingType(fit.scalingType()) },
                onRelease = { renderer ->
                    stream.detachRenderer(renderer)
                    renderer.release()
                },
            )
        }
    }
}

/**
 * The letterboxed picture of [stream] magnified and moved per [zoom] and clipped to
 * [modifier]'s bounds: drawn into a TextureView ([WebRtcTextureRenderer]), which Compose can
 * clip, laid out at the zoomed size up to [MAX_ZOOMED_SURFACE_SIDE] (so the frame is drawn at
 * that resolution rather than upscaled) with the video's aspect ratio once the first frame
 * tells it.
 */
@Composable
private fun ZoomedWebRtcSurface(stream: WebRtcStream, modifier: Modifier, zoom: VideoZoom) {
    Box(modifier.clipToBounds()) {
        Box(Modifier.zoomed(zoom, MAX_ZOOMED_SURFACE_SIDE).fillMaxSize(), contentAlignment = Alignment.Center) {
            key(stream) {
                var videoAspect by remember { mutableFloatStateOf(0f) }
                AndroidView(
                    factory = { context ->
                        val renderer = WebRtcTextureRenderer(WebRtcEngine.get(context).eglBase.eglBaseContext) { width, height ->
                            if (width > 0 && height > 0) videoAspect = width.toFloat() / height
                        }
                        TextureView(context).apply {
                            surfaceTextureListener = renderer
                            tag = renderer
                            stream.attachRenderer(renderer)
                        }
                    },
                    modifier = if (videoAspect > 0f) Modifier.aspectRatio(videoAspect) else Modifier.fillMaxSize(),
                    onRelease = { view ->
                        val renderer = view.tag as WebRtcTextureRenderer
                        stream.detachRenderer(renderer)
                        renderer.release()
                    },
                )
            }
        }
    }
}

private fun FitMode.scalingType() = when (this) {
    FitMode.FIT -> RendererCommon.ScalingType.SCALE_ASPECT_FIT
    FitMode.CROP -> RendererCommon.ScalingType.SCALE_ASPECT_FILL
}
