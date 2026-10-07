import CamGridShared
import SwiftUI
import UIKit

/// The iOS app: one window showing the Kotlin UI (MainViewController in shared/src/iosMain).
@main
struct CamGridIOSApp: App {
    @StateObject private var systemBars = SystemBars()

    var body: some Scene {
        WindowGroup {
            ComposeView(systemBars: systemBars)
                // Compose pads for the notch and home indicator itself.
                .ignoresSafeArea()
                .statusBarHidden(systemBars.immersive)
                .modifier(HomeIndicatorHidden(hidden: systemBars.immersive))
        }
    }
}

/// Hosts the Compose view controller.
struct ComposeView: UIViewControllerRepresentable {
    let systemBars: SystemBars

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(streams: StreamFactory(), systemBars: systemBars)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Whether the camera grid or a fullscreen camera is shown, set from Kotlin.
final class SystemBars: NSObject, ObservableObject, SystemBarsHost {
    @Published var immersive = false

    func onImmersiveChange(immersive: Bool) {
        // Outside the current SwiftUI update.
        DispatchQueue.main.async { self.immersive = immersive }
    }
}

/// Hides the home indicator while cameras are shown (iOS 16 and later).
private struct HomeIndicatorHidden: ViewModifier {
    let hidden: Bool

    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.persistentSystemOverlays(hidden ? .hidden : .automatic)
        } else {
            content
        }
    }
}
