package io.github.vandosketch.camgrid.player

import android.content.Context
import android.media.AudioAttributes
import io.github.vandosketch.camgrid.core.H264OfferOrder
import io.github.vandosketch.camgrid.core.VideoCodecSpec
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoCodecInfo
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

    override fun createDecoder(info: VideoCodecInfo): VideoDecoder? =
        delegate.createDecoder(info) ?: if (info.name.equals(H264, ignoreCase = true)) {
            delegate.supportedCodecs.firstOrNull { it.name.equals(H264, ignoreCase = true) }?.let(delegate::createDecoder)
        } else {
            null
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
