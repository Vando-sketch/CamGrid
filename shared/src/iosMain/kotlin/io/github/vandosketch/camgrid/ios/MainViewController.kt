package io.github.vandosketch.camgrid.ios

import androidx.compose.ui.window.ComposeUIViewController
import io.github.vandosketch.camgrid.CamGridViewModel
import io.github.vandosketch.camgrid.about.AppVersion
import io.github.vandosketch.camgrid.data.CamGridHttp
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.RemoteConfigClient
import io.github.vandosketch.camgrid.data.WebRtcOfferExchange
import io.github.vandosketch.camgrid.data.WhepClient
import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.ui.CamGridApp
import io.github.vandosketch.camgrid.ui.CamGridTheme
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/**
 * The whole app as one view controller, for the Swift app (iosApp/CamGrid/CamGridApp.swift):
 * `MainViewControllerKt.MainViewController(streams:systemBars:)`. Call once; the
 * view model lives as long as the app.
 *
 * @param streams the Swift video players.
 * @param systemBars hides the status bar and home indicator while the grid or a fullscreen
 *   camera is shown.
 */
fun MainViewController(streams: NativeStreamFactory, systemBars: SystemBarsHost): UIViewController {
    AppLog.sink = AppLog.Sink { level, tag, message -> NSLog("%@", "$level $tag: $message") }
    val viewModel = CamGridViewModel(
        configStore = IosConfigStore(),
        backupFiles = IosBackupFiles(),
        go2rtcClient = Go2rtcClient(CamGridHttp.client),
        remoteConfigClient = RemoteConfigClient(CamGridHttp.client),
        devicePreferences = IosDevicePreferences(),
    )
    val video = IosVideoPlatform(streams, WebRtcOfferExchange(WhepClient(CamGridHttp.client)))
    // MARKETING_VERSION and CURRENT_PROJECT_VERSION from iosApp/project.yml, which CI overrides.
    val info = NSBundle.mainBundle.infoDictionary
    val appVersion = AppVersion.fromBundle(
        shortVersion = info?.get("CFBundleShortVersionString") as? String,
        buildNumber = info?.get("CFBundleVersion") as? String,
    )
    return ComposeUIViewController {
        CamGridTheme {
            CamGridApp(
                viewModel = viewModel,
                video = video,
                appVersion = appVersion,
                onImmersiveChange = { immersive ->
                    systemBars.onImmersiveChange(immersive)
                    // A wall monitor: the screen stays on while cameras are shown.
                    UIApplication.sharedApplication.idleTimerDisabled = immersive
                },
            )
        }
    }
}
