import CamGridShared
import Foundation

/// Creates the native players for the Kotlin side (NativeStreamFactory in shared/src/iosMain).
/// Kotlin decides when to create and close them and shows their status; see NativeStreams.kt.
final class StreamFactory: NSObject, NativeStreamFactory {
    func createRtspStream(url: String, audioEnabled: Bool, events: NativeStreamEvents) -> NativeStream {
        VlcStream(url: url, audioEnabled: audioEnabled, events: events)
    }

    func createWebRtcStream(audioEnabled: Bool, signaling: WebRtcOfferSender, events: NativeStreamEvents) -> NativeStream {
        WebRtcStream(audioEnabled: audioEnabled, signaling: signaling, events: events)
    }
}
