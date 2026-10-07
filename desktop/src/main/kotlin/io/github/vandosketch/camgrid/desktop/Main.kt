package io.github.vandosketch.camgrid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vandosketch.camgrid.CamGridViewModel
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.core.UrlRedactor
import io.github.vandosketch.camgrid.data.CamGridHttp
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.desktop.config.DesktopBackupFiles
import io.github.vandosketch.camgrid.desktop.config.DesktopConfigStore
import io.github.vandosketch.camgrid.desktop.config.WindowStateStore
import io.github.vandosketch.camgrid.desktop.video.DesktopVideoPlatform
import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.ui.CamGridApp
import io.github.vandosketch.camgrid.ui.CamGridTheme
import io.github.vandosketch.camgrid.ui.LocalHasKeyboardAndMouse
import javax.imageio.ImageIO

/**
 * The desktop app. With stream URLs as arguments (or in CAMGRID_URLS, separated by spaces or
 * commas) it opens a bare 2x2 test grid of those streams instead, a developer tool for trying
 * the players without touching the saved config: `rtsp://` URLs play over RTSP, anything else
 * over WebRTC (go2rtc's `/api/webrtc?src=...`).
 *
 * The window comes back with the size, position and placement it had when last closed. F11
 * toggles window fullscreen (not a bare F: text fields leave letter key presses unconsumed), and on the camera wall and fullscreen the mouse cursor hides
 * while it rests.
 */
fun main(args: Array<String>) {
    AppLog.sink = AppLog.Sink { level, tag, message -> System.err.println("$level $tag: $message") }
    val testUrls = args.toList().ifEmpty {
        System.getenv("CAMGRID_URLS").orEmpty().split(' ', ',', '\n').filter { it.isNotBlank() }
    }
    val icon = Thread.currentThread().contextClassLoader.getResourceAsStream("camgrid.png")
        ?.use { ImageIO.read(it) }
        ?.let { BitmapPainter(it.toComposeImageBitmap()) }

    application {
        val windowStore = remember { WindowStateStore.forThisUser() }
        val windowState = remember { initialWindowState(windowStore.load()) }
        val initialWindow = remember { windowState.toSaved(previous = null) }
        SaveWindowState(windowState, windowStore, initialWindow)
        var immersive by remember { mutableStateOf(false) }
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "CamGrid",
            icon = icon,
            onPreviewKeyEvent = { event ->
                // F11 toggles fullscreen from anywhere, like browsers and video players.
                if (event.isPlainPress(Key.F11)) {
                    windowState.toggleFullscreen()
                    true
                } else {
                    false
                }
            },
        ) {
            CamGridTheme {
                CompositionLocalProvider(LocalHasKeyboardAndMouse provides true) {
                    if (testUrls.isNotEmpty()) {
                        TestGrid(testUrls)
                    } else {
                        AutoHideCursor(enabled = immersive) { App(onImmersiveChange = { immersive = it }) }
                    }
                }
            }
        }
    }
}

/** The app; [onImmersiveChange] reports the grid and fullscreen, where the cursor may hide. */
@Composable
private fun App(onImmersiveChange: (Boolean) -> Unit) {
    val viewModel = remember {
        CamGridViewModel(
            configStore = DesktopConfigStore.forThisUser(),
            backupFiles = DesktopBackupFiles(),
            go2rtcClient = Go2rtcClient(CamGridHttp.client),
        )
    }
    // A window is not a phone screen: the grid and fullscreen leave the window as it is (F11
    // makes it fullscreen); immersive only lets the idle mouse cursor hide.
    CamGridApp(viewModel = viewModel, video = DesktopVideoPlatform, onImmersiveChange = onImmersiveChange)
}

/** The developer test grid: up to four streams, muted, with their status. */
@Composable
private fun TestGrid(urls: List<String>) {
    val cells = urls.take(4) + List((4 - urls.size).coerceAtLeast(0)) { null }
    Column(Modifier.fillMaxSize()) {
        for (row in cells.chunked(2)) {
            Row(Modifier.weight(1f)) {
                for (url in row) {
                    Box(Modifier.weight(1f).fillMaxSize().padding(1.dp)) {
                        if (url != null) TestTile(url)
                    }
                }
            }
        }
    }
}

@Composable
private fun TestTile(url: String) {
    val type = if (url.startsWith("rtsp", ignoreCase = true)) StreamType.RTSP else StreamType.WEBRTC
    val label = UrlRedactor.redact(url)
    val stream = DesktopVideoPlatform.rememberLiveStream(url, type, label, audioEnabled = false)
    DesktopVideoPlatform.Surface(stream, Modifier.fillMaxSize(), FitMode.FIT)
    val status = when (val s = stream?.status) {
        StreamStatus.Playing -> null
        is StreamStatus.Offline -> "${s.reason}, retry in ${s.retryInSeconds} s"
        else -> "Connecting"
    }
    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomStart) {
        Text("$type $label" + (status?.let { "  ·  $it" } ?: ""), color = Color.White)
    }
}
