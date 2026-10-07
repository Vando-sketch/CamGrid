package io.github.vandosketch.camgrid.player

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import io.github.vandosketch.camgrid.core.H264OfferOrder
import io.github.vandosketch.camgrid.core.VideoCodecSpec
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.EncodedImage
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoDecoder
import org.webrtc.VideoDecoderFactory
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

/**
 * The app-wide WebRTC objects, created on first use and kept for the life of the process:
 * one [PeerConnectionFactory] and one EGL context that decoders and renderers share, so
 * decoded frames stay on the GPU. Receive-only: there is no video encoder and the microphone
 * is never opened (it would only start for a sending audio track, and CamGrid has none).
 */
class WebRtcEngine private constructor(context: Context) {
    val eglBase: EglBase = EglBase.create()

    val factory: PeerConnectionFactory

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
        )
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            // Media, not the default voice-call stream: the remote's volume keys control media.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(Go2rtcVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext)))
            .createPeerConnectionFactory()
        // The factory keeps its own reference to the module.
        audioDeviceModule.release()
    }

    /**
     * Disposes [peerConnection] off the main thread: dispose() blocks until WebRTC's own threads
     * have torn it down, and a page switch disposes up to 16 at once.
     */
    fun disposeLater(peerConnection: PeerConnection) {
        disposer.execute { peerConnection.dispose() }
    }

    private val disposer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CamGrid-WebRTC-dispose").apply { isDaemon = true }
    }

    companion object {
        private var instance: WebRtcEngine? = null

        /** Main thread only. */
        fun get(context: Context): WebRtcEngine =
            instance ?: WebRtcEngine(context.applicationContext).also { instance = it }
    }
}

/**
 * Offers H264 the way go2rtc needs it (see [H264OfferOrder]): Constrained Baseline first, so
 * go2rtc sends on a payload type that its answer accepts, plus High and Main, which libwebrtc
 * only advertises on Qualcomm and Exynos. The hardware decoder handles every profile; a profile
 * the delegate has no decoder entry for is decoded by its first H264 decoder.
 */
internal class Go2rtcVideoDecoderFactory(private val delegate: VideoDecoderFactory) : VideoDecoderFactory {

    override fun createDecoder(info: VideoCodecInfo): VideoDecoder? {
        val decoder = delegate.createDecoder(info) ?: if (info.name.equals(H264, ignoreCase = true)) {
            delegate.supportedCodecs.firstOrNull { it.name.equals(H264, ignoreCase = true) }?.let(delegate::createDecoder)
        } else {
            null
        }
        return decoder?.let { MonitoredVideoDecoder(it, info.name) }
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        val codecs = delegate.supportedCodecs.toList()
        val template = codecs.firstOrNull { it.name.equals(H264, ignoreCase = true) } ?: return codecs.toTypedArray()
        val h264 = H264OfferOrder.apply(listOf(VideoCodecSpec(template.name, template.params)))
            .map { VideoCodecInfo(it.name, it.params, template.scalabilityModes) }
        return (h264 + codecs.filterNot { it.name.equals(H264, ignoreCase = true) }).toTypedArray()
    }

    private companion object {
        const val H264 = "H264"
    }
}

/**
 * What the app's WebRTC video decoders reported last, for [WebRtcStream]'s failure reasons and
 * logs. libwebrtc does not tell which stream a decoder belongs to, so this is app-wide: exact
 * in fullscreen, where one stream plays, and a hint in the grid.
 */
internal object WebRtcDecoderReports {
    /** [status] of a decoder call for [codec] at [width]x[height] by [decoder], at [atMillis] (elapsedRealtime). */
    class Report(val atMillis: Long, val codec: String, val decoder: String, val width: Int, val height: Int, val status: String) {
        override fun toString() = "$codec ${width}x$height, decoder $decoder: $status"
    }

    /** The last decoder call that failed. */
    @Volatile var lastError: Report? = null

    /** The last decoder set up or given a new frame size, whatever its result. */
    @Volatile var lastSetup: Report? = null
}

/**
 * Passes everything to [delegate] and records failures and frame sizes in
 * [WebRtcDecoderReports]. A Fire TV's MediaCodec decoder that cannot take a stream's size
 * (portrait 1536x2048 on a 1080p decoder) fails in initDecode or decode, and libwebrtc has no
 * software H.264 decoder on Android to fall back to, so no frame ever arrives. A decoder that
 * runs natively ([createNative] not 0) is used by libwebrtc directly and reports nothing here.
 */
internal class MonitoredVideoDecoder(private val delegate: VideoDecoder, private val codec: String) : VideoDecoder {
    private var width = 0
    private var height = 0

    override fun createNative(webrtcEnvRef: Long): Long = delegate.createNative(webrtcEnvRef)

    override fun initDecode(settings: VideoDecoder.Settings, decodeCallback: VideoDecoder.Callback): VideoCodecStatus {
        width = settings.width
        height = settings.height
        return delegate.initDecode(settings, decodeCallback).also { report(it, setup = true) }
    }

    override fun decode(frame: EncodedImage, info: VideoDecoder.DecodeInfo): VideoCodecStatus {
        // Key frames carry the size; a new size makes the decoder reconfigure itself.
        val resized = frame.encodedWidth > 0 && frame.encodedHeight > 0 &&
            (frame.encodedWidth != width || frame.encodedHeight != height)
        if (resized) {
            width = frame.encodedWidth
            height = frame.encodedHeight
        }
        return delegate.decode(frame, info).also { report(it, setup = resized) }
    }

    override fun release(): VideoCodecStatus = delegate.release()

    override fun getImplementationName(): String = delegate.implementationName

    private fun report(status: VideoCodecStatus, setup: Boolean) {
        val failed = status !in HARMLESS
        if (!failed && !setup) return
        val report = WebRtcDecoderReports.Report(SystemClock.elapsedRealtime(), codec, delegate.implementationName, width, height, status.name)
        if (setup) WebRtcDecoderReports.lastSetup = report
        if (failed) WebRtcDecoderReports.lastError = report
    }

    private companion object {
        val HARMLESS = setOf(VideoCodecStatus.OK, VideoCodecStatus.NO_OUTPUT, VideoCodecStatus.TARGET_BITRATE_OVERSHOOT)
    }
}
