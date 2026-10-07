package io.github.vandosketch.camgrid.ios

import platform.UIKit.UIView

/*
 * The seam between the Kotlin app and the video players, which are written in Swift
 * (iosApp/CamGrid/Players): Google's WebRTC framework and VLCKit only have Objective-C/Swift
 * APIs. Swift implements NativeStreamFactory and NativeStream; Kotlin implements the
 * callbacks. Kotlin keeps everything else: reconnecting with backoff, the frame watchdog,
 * status on the tiles (StreamSupervisor) and the WebRTC HTTP signalling (WebRtcOfferExchange).
 *
 * Threading: every call in both directions happens on the main thread. Swift hops to the main
 * queue before calling a callback.
 */

/** Creates one native player per connection attempt. Implemented in Swift. */
interface NativeStreamFactory {
    /**
     * Plays an RTSP(S) URL (with user-info credentials, if any) with low latency over TCP, or an
     * http(s) media URL such as go2rtc's MP4 (`/api/stream.mp4`), the fallback for WebRTC streams
     * in a codec the WebRTC framework does not offer.
     */
    fun createRtspStream(url: String, audioEnabled: Boolean, events: NativeStreamEvents): NativeStream

    /**
     * Opens a receive-only WebRTC connection: the player creates its offer (all ICE candidates
     * gathered, no trickle), hands it to [signaling] and applies the answer it gets back.
     */
    fun createWebRtcStream(audioEnabled: Boolean, signaling: WebRtcOfferSender, events: NativeStreamEvents): NativeStream
}

/** One native player for one connection attempt. Implemented in Swift. */
interface NativeStream {
    /** The video, filling its bounds; Kotlin puts it into the tile. */
    val view: UIView

    /** Only has an effect on a stream created with audio enabled. */
    fun setMuted(muted: Boolean)

    /** true: fill the bounds and crop the video; false: show it whole, letterboxed. */
    fun setCrop(crop: Boolean)

    /** Video frames decoded so far, or -1 while the player cannot tell. Polled once a second. */
    fun decodedFrames(): Long

    /** Stops playback and frees the player. No callback is made afterwards. */
    fun close()
}

/** What a player reports. Implemented in Kotlin; call on the main thread. */
interface NativeStreamEvents {
    /** Video is showing (the first frame has been decoded, or the player started playing). */
    fun onPlaying()

    /** Playback failed or ended. [reason] is a short code such as `ICE_FAILED`, never a URL. */
    fun onFailed(reason: String)
}

/** Sends a WebRTC offer to the camera's signalling URL. Implemented in Kotlin. */
interface WebRtcOfferSender {
    /** Exactly one of [WebRtcAnswerHandler]'s methods is called later, unless the stream closes first. */
    fun sendOffer(offerSdp: String, handler: WebRtcAnswerHandler)
}

/** Receives the WebRTC answer. Implemented in Swift. */
interface WebRtcAnswerHandler {
    fun onAnswer(answerSdp: String)

    fun onSignalingFailed(reason: String)
}

/** Shows or hides the status bar and home indicator for the grid and fullscreen. Implemented in Swift. */
interface SystemBarsHost {
    fun onImmersiveChange(immersive: Boolean)
}
