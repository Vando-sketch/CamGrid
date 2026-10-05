package io.github.vandosketch.camgrid

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import io.github.vandosketch.camgrid.player.StreamPlayer
import io.github.vandosketch.camgrid.ui.CamGridApp
import io.github.vandosketch.camgrid.ui.CamGridTheme

/** The only activity. Opens straight into the camera grid. */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: CamGridViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // No enableEdgeToEdge(): targetSdk 36 makes Android 15+ edge-to-edge anyway, and the
        // settings screens pad themselves with the safe-drawing insets on every version.
        super.onCreate(savedInstanceState)
        StreamPlayer.disableLibraryLogging()
        viewModel = ViewModelProvider(this)[CamGridViewModel::class.java]

        setContent {
            CamGridTheme {
                CamGridApp(viewModel = viewModel, onImmersiveChange = ::setSystemBarsHidden)
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
