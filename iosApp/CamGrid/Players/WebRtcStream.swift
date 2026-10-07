import CamGridShared
import UIKit
import WebRTC

/// The app-wide WebRTC objects. Receive-only: no camera or microphone track is ever created.
enum WebRtcEngine {
    static let factory: RTCPeerConnectionFactory = {
        RTCInitializeSSL()
        // Grid tiles negotiate no audio; the audio engine only starts while a stream with
        // sound (fullscreen) is open.
        let audioSession = RTCAudioSession.sharedInstance()
        audioSession.useManualAudio = true
        audioSession.isAudioEnabled = false
        return RTCPeerConnectionFactory(
            encoderFactory: RTCDefaultVideoEncoderFactory(),
            decoderFactory: RTCDefaultVideoDecoderFactory()
        )
    }()

    /// Open streams with audio; main thread only.
    static var audioStreams = 0 {
        didSet { RTCAudioSession.sharedInstance().isAudioEnabled = audioStreams > 0 }
    }

    /// close() blocks until WebRTC's threads are done; a page switch closes up to 16 at once.
    static let closeQueue = DispatchQueue(label: "io.github.vandosketch.camgrid.webrtc-close")
}

/// One receive-only WebRTC connection to go2rtc (or another WHEP-style server). Main thread
/// only. The HTTP exchange of offer and answer is done by Kotlin (WebRtcOfferSender), which also
/// puts H264 Constrained Baseline first in the offer, as go2rtc needs.
final class WebRtcStream: NSObject, NativeStream, WebRtcAnswerHandler, RTCPeerConnectionDelegate {
    let view: UIView

    private let videoView: RTCMTLVideoView
    private let frames = FrameCounter()
    private let audioEnabled: Bool
    private var events: NativeStreamEvents?
    private var signaling: WebRtcOfferSender?
    private var peerConnection: RTCPeerConnection?
    private var videoTrack: RTCVideoTrack?
    private var audioTrack: RTCAudioTrack?
    private var muted = false
    private var offerSent = false
    private var closed = false

    init(audioEnabled: Bool, signaling: WebRtcOfferSender, events: NativeStreamEvents) {
        let videoView = RTCMTLVideoView(frame: .zero)
        videoView.videoContentMode = .scaleAspectFit
        videoView.backgroundColor = .black
        videoView.clipsToBounds = true
        videoView.isUserInteractionEnabled = false
        self.videoView = videoView
        self.view = videoView
        self.audioEnabled = audioEnabled
        self.signaling = signaling
        self.events = events
        super.init()
        if audioEnabled {
            WebRtcEngine.audioStreams += 1
        }
        start()
    }

    func setMuted(muted: Bool) {
        self.muted = muted
        audioTrack?.isEnabled = !muted
    }

    func setCrop(crop: Bool) {
        videoView.videoContentMode = crop ? .scaleAspectFill : .scaleAspectFit
    }

    func decodedFrames() -> Int64 {
        closed ? -1 : frames.count
    }

    func close() {
        guard !closed else { return }
        closed = true
        events = nil
        signaling = nil
        if audioEnabled {
            WebRtcEngine.audioStreams -= 1
        }
        videoTrack?.remove(videoView)
        videoTrack?.remove(frames)
        videoTrack = nil
        audioTrack = nil
        if let peerConnection = peerConnection {
            self.peerConnection = nil
            WebRtcEngine.closeQueue.async { peerConnection.close() }
        }
    }

    // MARK: Offer and answer

    private func start() {
        let config = RTCConfiguration()
        // No STUN or TURN: CamGrid is for the local network, where host candidates connect.
        config.iceServers = []
        config.sdpSemantics = .unifiedPlan
        config.bundlePolicy = .maxBundle
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        let created: RTCPeerConnection? = WebRtcEngine.factory.peerConnection(
            with: config, constraints: constraints, delegate: self)
        guard let peerConnection = created else {
            DispatchQueue.main.async { [weak self] in self?.fail("NO_PEER_CONNECTION") }
            return
        }
        self.peerConnection = peerConnection
        let receiveOnly = RTCRtpTransceiverInit()
        receiveOnly.direction = .recvOnly
        _ = peerConnection.addTransceiver(of: .video, init: receiveOnly)
        if audioEnabled {
            _ = peerConnection.addTransceiver(of: .audio, init: receiveOnly)
        }
        peerConnection.offer(for: constraints) { [weak self] offer, _ in
            DispatchQueue.main.async { self?.setLocalOffer(offer) }
        }
    }

    private func setLocalOffer(_ offer: RTCSessionDescription?) {
        guard !closed, let peerConnection = peerConnection else { return }
        guard let offer = offer else {
            fail("OFFER_FAILED")
            return
        }
        peerConnection.setLocalDescription(offer) { [weak self] error in
            DispatchQueue.main.async {
                guard let self = self, !self.closed else { return }
                if error != nil {
                    self.fail("OFFER_FAILED")
                    return
                }
                // go2rtc takes no trickled candidates: wait for gathering to finish, or send
                // what there is after 2 s (host candidates on a LAN gather almost at once).
                if self.peerConnection?.iceGatheringState == .complete {
                    self.sendOffer()
                } else {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in self?.sendOffer() }
                }
            }
        }
    }

    private func sendOffer() {
        guard !closed, !offerSent, let sdp = peerConnection?.localDescription?.sdp else { return }
        offerSent = true
        signaling?.sendOffer(offerSdp: sdp, handler: self)
    }

    func onAnswer(answerSdp: String) {
        guard !closed, let peerConnection = peerConnection else { return }
        let answer = RTCSessionDescription(type: .answer, sdp: answerSdp)
        peerConnection.setRemoteDescription(answer) { [weak self] error in
            guard error != nil else { return }
            DispatchQueue.main.async { self?.fail("ANSWER_REJECTED") }
        }
    }

    func onSignalingFailed(reason: String) {
        fail(reason)
    }

    private func fail(_ reason: String) {
        guard !closed else { return }
        events?.onFailed(reason: reason)
    }

    // MARK: RTCPeerConnectionDelegate (called on WebRTC's signalling thread)

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {
        guard newState == .complete else { return }
        DispatchQueue.main.async { [weak self] in self?.sendOffer() }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCPeerConnectionState) {
        // .disconnected often recovers by itself; Kotlin's frame watchdog catches it if not.
        guard newState == .failed else { return }
        DispatchQueue.main.async { [weak self] in self?.fail("ICE_FAILED") }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd rtpReceiver: RTCRtpReceiver, streams mediaStreams: [RTCMediaStream]) {
        let track = rtpReceiver.track
        DispatchQueue.main.async { [weak self] in
            guard let self = self, !self.closed else { return }
            if let video = track as? RTCVideoTrack {
                video.add(self.videoView)
                video.add(self.frames)
                self.videoTrack = video
            } else if let audio = track as? RTCAudioTrack {
                audio.isEnabled = !self.muted
                self.audioTrack = audio
            }
        }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}

    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
}

/// Counts decoded frames (on a WebRTC decoder thread) for Kotlin's stall watchdog.
private final class FrameCounter: NSObject, RTCVideoRenderer {
    private let lock = NSLock()
    private var frames: Int64 = 0

    var count: Int64 {
        lock.lock()
        defer { lock.unlock() }
        return frames
    }

    func setSize(_ size: CGSize) {}

    func renderFrame(_ frame: RTCVideoFrame?) {
        guard frame != nil else { return }
        lock.lock()
        frames += 1
        lock.unlock()
    }
}
