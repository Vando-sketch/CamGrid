package io.github.vandosketch.camgrid.player

import android.content.Context
import android.media.AudioAttributes
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
            .setVideoDecoderFactory(H264ProfilesDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext)))
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
 * libwebrtc advertises H264 High profile only for a few chip vendors (Qualcomm, Exynos), so on a
 * MediaTek Fire TV the offer would list Constrained Baseline only, and a camera's High or Main
 * profile stream may not negotiate. MediaCodec decodes those profiles fine, so this factory adds
 * them to the advertised H264 variants and decodes them with the same hardware decoder.
 */
internal class H264ProfilesDecoderFactory(private val delegate: VideoDecoderFactory) : VideoDecoderFactory {

    override fun createDecoder(info: VideoCodecInfo): VideoDecoder? =
        delegate.createDecoder(info) ?: if (info.name.equals(H264, ignoreCase = true)) {
            delegate.supportedCodecs.firstOrNull { it.name.equals(H264, ignoreCase = true) }?.let(delegate::createDecoder)
        } else {
            null
        }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        val codecs = delegate.supportedCodecs.toMutableList()
        val template = codecs.firstOrNull { it.name.equals(H264, ignoreCase = true) } ?: return codecs.toTypedArray()
        val advertisedProfiles = codecs
            .filter { it.name.equals(H264, ignoreCase = true) }
            .mapNotNull { it.params[VideoCodecInfo.H264_FMTP_PROFILE_LEVEL_ID]?.take(PROFILE_CHARS)?.lowercase() }
            .toSet()
        for (profileLevelId in EXTRA_PROFILE_LEVEL_IDS) {
            if (profileLevelId.take(PROFILE_CHARS) in advertisedProfiles) continue
            val params = template.params + (VideoCodecInfo.H264_FMTP_PROFILE_LEVEL_ID to profileLevelId)
            codecs += VideoCodecInfo(template.name, params, template.scalabilityModes)
        }
        return codecs.toTypedArray()
    }

    private companion object {
        const val H264 = "H264"

        /** profile_idc and profile_iop; the level (last two hex digits) does not affect matching. */
        const val PROFILE_CHARS = 4

        /** Constrained High, High and Main, all at level 3.1. */
        val EXTRA_PROFILE_LEVEL_IDS = listOf("640c1f", "64001f", "4d001f")
    }
}
