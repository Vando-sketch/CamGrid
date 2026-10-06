package io.github.vandosketch.camgrid.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.data.WebRtcOfferExchange
import io.github.vandosketch.camgrid.platform.LiveStream
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.StreamSupervisor
import io.github.vandosketch.camgrid.platform.VideoPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * iOS players: VLCKit for [StreamType.RTSP] and Google's WebRTC framework for
 * [StreamType.WEBRTC], both in Swift behind [NativeStreamFactory]. Kotlin supervises them
 * ([StreamSupervisor]) and embeds their views in the Compose tiles.
 */
class IosVideoPlatform(
    private val factory: NativeStreamFactory,
    private val offers: WebRtcOfferExchange,
) : VideoPlatform {

    override val supportedTypes: Set<StreamType> = setOf(StreamType.RTSP, StreamType.WEBRTC)

    /**
     * A stream that exists only while the app is in the foreground and the composable is in the
     * composition, like on Android: released when the app goes to the background.
     */
    @Composable
    override fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream? {
        var stream by remember { mutableStateOf<NativeLiveStream?>(null) }
        LifecycleStartEffect(url, type, audioEnabled) {
            val created = NativeLiveStream(factory, offers, url, type, label, audioEnabled)
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
        Box(modifier.background(Color.Black).clipToBounds()) {
            val native = (stream as? NativeLiveStream)?.current ?: return@Box
            // A new native player (after a reconnect) gets a new interop view.
            key(native) {
                UIKitView(
                    factory = {
                        native.view.apply {
                            clipsToBounds = true
                            userInteractionEnabled = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    // Taps go to the Compose tile, not the video view.
                    properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false),
                )
                LaunchedEffect(native, fit) { native.setCrop(fit == FitMode.CROP) }
            }
        }
    }
}

/** One camera stream: a [StreamSupervisor] that opens a new Swift player for every attempt. */
internal class NativeLiveStream(
    private val factory: NativeStreamFactory,
    private val offers: WebRtcOfferExchange,
    private val url: String,
    private val type: StreamType,
    label: String,
    private val audioEnabled: Boolean,
) : LiveStream {

    private val scope = MainScope()
    private var muted: Boolean? = null

    private val supervisor = StreamSupervisor(scope, label, open = ::open)

    init {
        supervisor.start()
    }

    override val status: StreamStatus
        get() = supervisor.status

    /** The current Swift player, null while waiting to reconnect; snapshot state. */
    val current: NativeStream?
        get() = supervisor.session?.native

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        current?.setMuted(muted)
    }

    override fun release() {
        supervisor.release()
        scope.cancel()
    }

    private fun open(callbacks: StreamSupervisor.Callbacks): Session {
        val events = object : NativeStreamEvents {
            override fun onPlaying() = callbacks.onPlaying()

            override fun onFailed(reason: String) = callbacks.onFailed(reason)
        }
        val native = when (type) {
            StreamType.RTSP -> factory.createRtspStream(url, audioEnabled, events)
            StreamType.WEBRTC -> factory.createWebRtcStream(audioEnabled, OfferSender(scope, offers, url), events)
        }
        muted?.let(native::setMuted)
        return Session(native)
    }

    class Session(val native: NativeStream) : StreamSupervisor.Session {
        override fun decodedFrames(): Long = native.decodedFrames()

        override fun close() = native.close()
    }
}

/** Runs the HTTP half of WebRTC signalling for a Swift player; cancelled with the stream. */
private class OfferSender(
    private val scope: CoroutineScope,
    private val offers: WebRtcOfferExchange,
    private val url: String,
) : WebRtcOfferSender {
    override fun sendOffer(offerSdp: String, handler: WebRtcAnswerHandler) {
        scope.launch {
            when (val result = offers.exchange(url, offerSdp)) {
                is WebRtcOfferExchange.Result.Answer -> handler.onAnswer(result.sdp)
                is WebRtcOfferExchange.Result.Failed -> handler.onSignalingFailed(result.reason)
            }
        }
    }
}
