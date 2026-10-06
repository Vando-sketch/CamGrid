import CamGridShared
import UIKit
import VLCKit

/// One RTSP stream played by libVLC (VLCKit) with low latency over TCP. Main thread only.
/// Kotlin reconnects after a failure by creating a new instance.
final class VlcStream: NSObject, NativeStream, VLCMediaPlayerDelegate {
    let view: UIView = {
        let view = UIView()
        view.backgroundColor = .black
        view.clipsToBounds = true
        view.isUserInteractionEnabled = false
        return view
    }()

    private var player: VLCMediaPlayer?
    private var events: NativeStreamEvents?
    private var reportedPlaying = false

    init(url: String, audioEnabled: Bool, events: NativeStreamEvents) {
        self.events = events
        super.init()
        guard let mediaURL = URL(string: url), let media = VLCMedia(url: mediaURL) else {
            DispatchQueue.main.async { [weak self] in self?.fail("INVALID_URL") }
            return
        }
        // Low latency: a small network buffer, RTP interleaved over the RTSP TCP connection
        // (no lost UDP packets on Wi-Fi), and no audio decoding at all for grid tiles.
        media.addOption(":network-caching=300")
        media.addOption(":rtsp-tcp")
        if !audioEnabled {
            media.addOption(":no-audio")
        }
        let player = VLCMediaPlayer()
        player.media = media
        player.drawable = view
        player.videoFitMode = .smaller
        player.delegate = self
        self.player = player
        player.play()
    }

    func setMuted(muted: Bool) {
        if let audio = player?.audio {
            audio.isMuted = muted
        }
    }

    func setCrop(crop: Bool) {
        // smaller: the whole picture, letterboxed; larger: fill the tile and crop.
        player?.videoFitMode = crop ? .larger : .smaller
    }

    func decodedFrames() -> Int64 {
        guard let media = player?.media else { return -1 }
        let decoded = media.statistics.decodedVideo
        // 0 also when statistics are unavailable: let Kotlin's watchdog start with the first frame.
        return decoded > 0 ? Int64(clamping: decoded) : -1
    }

    func close() {
        events = nil
        guard let player = player else { return }
        self.player = nil
        player.delegate = nil
        player.stop()
        // Freeing a player waits for its network thread; keep that off the main thread.
        DispatchQueue.global(qos: .utility).async {
            withExtendedLifetime(player) {}
        }
    }

    func mediaPlayerStateChanged(_ newState: VLCMediaPlayerState) {
        if Thread.isMainThread {
            handle(newState)
        } else {
            DispatchQueue.main.async { [weak self] in self?.handle(newState) }
        }
    }

    private func handle(_ state: VLCMediaPlayerState) {
        switch state {
        case .playing:
            if !reportedPlaying {
                reportedPlaying = true
                events?.onPlaying()
            }
        case .error:
            fail("ERROR")
        case .stopped:
            // A live stream never ends by itself: the connection dropped.
            fail("ENDED")
        default:
            break
        }
    }

    private func fail(_ reason: String) {
        events?.onFailed(reason: reason)
    }
}
