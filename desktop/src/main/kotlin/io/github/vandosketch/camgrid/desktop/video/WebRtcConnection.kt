package io.github.vandosketch.camgrid.desktop.video

import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.PeerConnectionObserver
import dev.onvoid.webrtc.RTCBundlePolicy
import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCIceGatheringState
import dev.onvoid.webrtc.RTCOfferOptions
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCPeerConnectionState
import dev.onvoid.webrtc.RTCRtpTransceiver
import dev.onvoid.webrtc.RTCRtpTransceiverDirection
import dev.onvoid.webrtc.RTCRtpTransceiverInit
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import dev.onvoid.webrtc.media.FourCC
import dev.onvoid.webrtc.media.audio.AudioTrack
import dev.onvoid.webrtc.media.video.VideoBufferConverter
import dev.onvoid.webrtc.media.video.VideoFrame
import dev.onvoid.webrtc.media.video.VideoTrack
import dev.onvoid.webrtc.media.video.VideoTrackSink
import io.github.vandosketch.camgrid.core.Go2rtc
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One receive-only WebRTC connection to a WHEP-style endpoint such as go2rtc's
 * `/api/webrtc?src=<name>` (webrtc-java, libwebrtc's own H.264 decoder). The same flow as the
 * Android app: recvonly transceivers, H.264 Constrained Baseline first in the offer, wait for
 * ICE gathering (no trickle), POST, apply the answer. Decoded frames are scaled to the
 * viewport and converted to BGRA into [frames].
 *
 * libwebrtc offers H.264, VP8, VP9 and AV1 here, never H.265 (it has no H.265 decoder of its
 * own), so go2rtc refuses an H.265 camera: HTTP 500 "codecs not matched" (`CODEC_H265`), or an
 * answer without video when the audio codec matched (`CODEC_UNSUPPORTED`). The platform then
 * plays go2rtc's MP4 of the stream with FFmpeg instead (`rememberStreamWithFallback`).
 *
 * @param audioEnabled false for grid tiles: only video is negotiated, so no audio is received.
 */
class WebRtcConnection(
    url: String,
    private val frames: FrameHolder,
    private val audioEnabled: Boolean,
) : StreamConnection {

    // Configs saved before the editor normalised URLs may hold a go2rtc player page URL.
    private val url = Go2rtc.webrtcEndpoint(url) ?: url

    @Volatile private var muted = false
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var closed = false

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        applyMute()
    }

    private fun applyMute() {
        // A disabled remote audio track is played silently by libwebrtc.
        audioTrack?.let { if (!closed) it.isEnabled = !muted }
    }

    override suspend fun play(frames: FrameListener) {
        val factory = WebRtcEngine.factory
        val failed = CompletableDeferred<String>()
        val gatheringComplete = CompletableDeferred<Unit>()
        val sink = Sink(frames)
        var videoTrack: VideoTrack? = null

        val observer = object : PeerConnectionObserver {
            override fun onIceCandidate(candidate: RTCIceCandidate) = Unit

            override fun onIceGatheringChange(state: RTCIceGatheringState) {
                if (state == RTCIceGatheringState.COMPLETE) gatheringComplete.complete(Unit)
            }

            override fun onConnectionChange(state: RTCPeerConnectionState) {
                // DISCONNECTED often recovers by itself; the watchdog catches it if it does not.
                if (state == RTCPeerConnectionState.FAILED) failed.complete("ICE_FAILED")
            }

            override fun onTrack(transceiver: RTCRtpTransceiver) {
                when (val track = transceiver.receiver.track) {
                    is VideoTrack -> {
                        videoTrack = track
                        track.addSink(sink)
                    }
                    is AudioTrack -> {
                        audioTrack = track
                        applyMute()
                    }
                    else -> Unit
                }
            }
        }

        // No STUN or TURN servers: CamGrid is LAN only, where host candidates connect directly.
        val config = RTCConfiguration().apply { bundlePolicy = RTCBundlePolicy.MAX_BUNDLE }
        val pc = factory.createPeerConnection(config, observer) ?: throw StreamFailure("NO_PEER_CONNECTION")
        try {
            val recvOnly = RTCRtpTransceiverInit().apply { direction = RTCRtpTransceiverDirection.RECV_ONLY }
            val video = pc.addTransceiver(WebRtcEngine.placeholderVideoTrack, recvOnly)
            try {
                video.setCodecPreferences(WebRtcEngine.videoCodecPreferences)
            } catch (e: Exception) {
                System.err.println("CamGrid: H.264 offer order not applied (${e.javaClass.simpleName})")
            }
            if (audioEnabled) pc.addTransceiver(WebRtcEngine.placeholderAudioTrack, recvOnly)

            val offer = pc.awaitOffer()
            awaitSet { pc.setLocalDescription(offer, it) }
            // The server does not take trickled candidates, so the offer must carry them all.
            // Host candidates on a LAN gather almost at once; send what there is after the timeout.
            withTimeoutOrNull(ICE_GATHERING_TIMEOUT_MS) { gatheringComplete.await() }
            val offerSdp = pc.localDescription?.sdp ?: offer.sdp
            val answerSdp = WhepClient.exchange(url, offerSdp)
            awaitSet("ANSWER_REJECTED") { pc.setRemoteDescription(RTCSessionDescription(RTCSdpType.ANSWER, answerSdp), it) }
            throw StreamFailure(failed.await())
        } finally {
            closed = true
            audioTrack = null
            val track = videoTrack
            // Closing waits for libwebrtc's threads; never block the caller (the UI thread).
            closer.execute {
                try {
                    track?.removeSink(sink)
                    pc.close()
                } catch (e: Exception) {
                    System.err.println("CamGrid: WebRTC close failed (${e.javaClass.simpleName})")
                }
            }
        }
    }

    /** Converts each decoded frame (on a libwebrtc decoder thread) into the frame holder. */
    private inner class Sink(private val listener: FrameListener) : VideoTrackSink {
        override fun onVideoFrame(frame: VideoFrame) {
            if (closed) return
            listener.onFrame()
            val buffer = frame.buffer
            val (width, height) = frames.targetSize(buffer.width, buffer.height)
            val scaled = if (width == buffer.width && height == buffer.height) {
                null
            } else {
                buffer.cropAndScale(0, 0, buffer.width, buffer.height, width, height)
            }
            try {
                // libyuv's "ARGB" is B, G, R, A in memory: Skia's BGRA_8888.
                frames.write(width, height) { pixels -> VideoBufferConverter.convertFromI420(scaled ?: buffer, pixels, FourCC.ARGB) }
            } finally {
                scaled?.release()
            }
        }
    }

    private suspend fun RTCPeerConnection.awaitOffer(): RTCSessionDescription = suspendCancellableCoroutine { cont ->
        createOffer(RTCOfferOptions(), object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) {
                if (cont.isActive) cont.resume(description)
            }

            override fun onFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(StreamFailure("OFFER_FAILED"))
            }
        })
    }

    private suspend fun awaitSet(failure: String = "OFFER_FAILED", call: (SetSessionDescriptionObserver) -> Unit): Unit =
        suspendCancellableCoroutine { cont ->
            call(object : SetSessionDescriptionObserver {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(StreamFailure(failure))
                }
            })
        }

    private companion object {
        const val ICE_GATHERING_TIMEOUT_MS = 2_000L

        /** One thread for all closes, so they never run concurrently with each other. */
        val closer = Executors.newSingleThreadExecutor { r -> Thread(r, "camgrid-webrtc-close").apply { isDaemon = true } }
    }
}
