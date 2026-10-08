package io.github.vandosketch.camgrid

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.vandosketch.camgrid.data.AndroidBackupFiles
import io.github.vandosketch.camgrid.data.AndroidConfigStore
import io.github.vandosketch.camgrid.data.AndroidDevicePreferences
import io.github.vandosketch.camgrid.data.CamGridHttp
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.RemoteConfigClient
import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.player.AndroidVideoPlatform
import io.github.vandosketch.camgrid.player.StreamPlayer
import io.github.vandosketch.camgrid.ui.CamGridApp
import io.github.vandosketch.camgrid.ui.CamGridTheme
import io.github.vandosketch.camgrid.ui.LocalDpadFirst

/** The only activity. Opens straight into the camera grid. */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: CamGridViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // No enableEdgeToEdge(): targetSdk 36 makes Android 15+ edge-to-edge anyway, and the
        // settings screens pad themselves with the safe-drawing insets on every version.
        super.onCreate(savedInstanceState)
        AppLog.sink = AppLog.Sink { level, tag, message ->
            when (level) {
                AppLog.Level.WARN -> Log.w(tag, message)
                AppLog.Level.ERROR -> Log.e(tag, message)
            }
        }
        StreamPlayer.disableLibraryLogging()
        val factory = viewModelFactory {
            initializer {
                CamGridViewModel(
                    configStore = AndroidConfigStore(application),
                    backupFiles = AndroidBackupFiles(application),
                    go2rtcClient = Go2rtcClient(CamGridHttp.client),
                    remoteConfigClient = RemoteConfigClient(CamGridHttp.client),
                    devicePreferences = AndroidDevicePreferences(application),
                )
            }
        }
        viewModel = ViewModelProvider(this, factory)[CamGridViewModel::class.java]

        // On a TV the D-pad only highlights text fields, so it can move past them; OK types.
        val dpadFirst = isTvDevice()
        val autoStart = AndroidAutoStart(applicationContext)
        setContent {
            CamGridTheme {
                CompositionLocalProvider(LocalDpadFirst provides dpadFirst) {
                    CamGridApp(
                        viewModel = viewModel,
                        video = AndroidVideoPlatform,
                        appVersion = BuildConfig.VERSION_NAME,
                        onImmersiveChange = ::setSystemBarsHidden,
                        autoStart = autoStart,
                    )
                }
            }
        }
    }

    /** MENU on the Fire TV remote opens the settings from the grid or fullscreen. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && viewModel.onMenuKey()) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun setSystemBarsHidden(hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
