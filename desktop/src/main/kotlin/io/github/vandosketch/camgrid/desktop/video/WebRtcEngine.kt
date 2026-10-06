package io.github.vandosketch.camgrid.desktop.video

import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.RTCRtpCodecCapability
import dev.onvoid.webrtc.media.MediaType
import dev.onvoid.webrtc.media.audio.AudioDeviceModule
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.audio.AudioTrack
import dev.onvoid.webrtc.media.audio.HeadlessAudioDeviceModule
import dev.onvoid.webrtc.media.video.CustomVideoSource
import dev.onvoid.webrtc.media.video.VideoTrack
import io.github.vandosketch.camgrid.core.VideoCodecSpec

/**
 * The one libwebrtc instance of the app (webrtc-java). Created on first use; its audio device
 * module plays the received audio of streams that have audio (fullscreen) on the default
 * output device. Without a usable audio device, or with the system property
 * `camgrid.webrtc.headlessAudio=true` (tests, CI), audio goes nowhere and video still plays.
 */
object WebRtcEngine {
    val factory: PeerConnectionFactory by lazy(::createFactory)

    /** The video receive codecs in the order go2rtc needs ([CodecPreferences]). */
    val videoCodecPreferences: List<RTCRtpCodecCapability> by lazy {
        val capabilities = factory.getRtpReceiverCapabilities(MediaType.VIDEO).codecs
        val specs = capabilities.map { VideoCodecSpec(it.name, it.sdpFmtp ?: emptyMap()) }
        CodecPreferences.order(specs).map { capabilities[it] }
    }

    /**
     * libwebrtc wants a track for each transceiver, even a receive-only one. These two are
     * shared by every connection and never send anything (nothing is ever pushed into them).
     */
    val placeholderVideoTrack: VideoTrack by lazy { factory.createVideoTrack("camgrid-video", CustomVideoSource()) }
    val placeholderAudioTrack: AudioTrack by lazy {
        factory.createAudioTrack("camgrid-audio", factory.createAudioSource(AudioOptions()))
    }

    private fun createFactory(): PeerConnectionFactory {
        if (System.getProperty("camgrid.webrtc.headlessAudio") != "true") {
            try {
                return PeerConnectionFactory(AudioDeviceModule())
            } catch (e: Exception) {
                System.err.println("CamGrid: no audio device for WebRTC (${e.javaClass.simpleName}); video only")
            }
        }
        return PeerConnectionFactory(HeadlessAudioDeviceModule())
    }
}
