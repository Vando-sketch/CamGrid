package io.github.vandosketch.camgrid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vandosketch.camgrid.CamGridViewModel
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.GridPosition
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.core.UrlRedactor
import io.github.vandosketch.camgrid.core.ViewPaging
import io.github.vandosketch.camgrid.data.CamGridHttp
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.RemoteConfigClient
import io.github.vandosketch.camgrid.desktop.autostart.DesktopAutoStart
import io.github.vandosketch.camgrid.desktop.config.DesktopBackupFiles
import io.github.vandosketch.camgrid.desktop.config.DesktopConfigStore
import io.github.vandosketch.camgrid.desktop.config.DesktopDevicePreferences
import io.github.vandosketch.camgrid.desktop.config.WindowStateStore
import io.github.vandosketch.camgrid.desktop.video.DesktopVideoPlatform
import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.ui.CamGridApp
import io.github.vandosketch.camgrid.ui.CamGridTheme
import io.github.vandosketch.camgrid.ui.KeyboardNavigation
import io.github.vandosketch.camgrid.ui.LocalHasKeyboardAndMouse
import io.github.vandosketch.camgrid.ui.LocalKeyboardNavigation
import java.awt.Rectangle
import javax.imageio.ImageIO
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull

/**
 * The desktop app. With stream URLs as arguments (or in CAMGRID_URLS, separated by spaces or
 * commas) it opens a bare 2x2 test grid of those streams instead, a developer tool for trying
 * the players without touching the saved config: `rtsp://` URLs play over RTSP, anything else
 * over WebRTC (go2rtc's `/api/webrtc?src=...`).
 *
 * The window comes back with the size, position and placement it had when last closed. F11
 * toggles window fullscreen (not a bare F: text fields leave letter key presses unconsumed), and on the camera wall and fullscreen the mouse cursor hides
 * while it rests.
 *
 * With Settings, Second monitor turned on, a second window shows a camera wall of its own
 * ([SecondWindow]): the same config, its own view, its own size and place. Closing it turns the
 * setting off; closing the main window quits both.
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
        if (testUrls.isNotEmpty()) {
            CamGridWindow(WindowStateStore.forThisUser(), icon, onCloseRequest = ::exitApplication) { TestGrid(testUrls) }
        } else {
            CamGridWindows(icon)
        }
    }
}

/** The main window and, when turned on, the second one. */
@Composable
private fun ApplicationScope.CamGridWindows(icon: Painter?) {
    val preferences = remember { DesktopDevicePreferences.forThisUser() }
    val viewModel = remember {
        CamGridViewModel(
            configStore = DesktopConfigStore.forThisUser(),
            backupFiles = DesktopBackupFiles(),
            go2rtcClient = Go2rtcClient(CamGridHttp.client),
            remoteConfigClient = RemoteConfigClient(CamGridHttp.client),
            devicePreferences = preferences,
        )
    }
    val autoStart = remember { DesktopAutoStart.forThisApp() }
    var secondWindow by remember { mutableStateOf(SecondWindow.isEnabled(preferences)) }
    val setSecondWindow: (Boolean) -> Unit = {
        secondWindow = it
        SecondWindow.setEnabled(preferences, it)
    }
    // The main window's bounds, for placing a new second window on another screen.
    val mainWindow = remember { arrayOfNulls<java.awt.Window>(1) }

    CamGridWindow(WindowStateStore.forThisUser(), icon, onCloseRequest = ::exitApplication) { immersive ->
        SideEffect { mainWindow[0] = window }
        // Settings asked for in the second window open here: bring this window forward.
        LaunchedEffect(viewModel) {
            snapshotFlow { viewModel.settingsRequests }.drop(1).collect {
                if (window.isMinimized) window.isMinimized = false
                window.toFront()
                window.requestFocus()
            }
        }
        CamGridApp(
            viewModel = viewModel,
            video = DesktopVideoPlatform,
            appVersion = DesktopAppVersion.current,
            // A window is not a phone screen: the grid and fullscreen leave the window as it is (F11
            // makes it fullscreen); immersive only lets the idle mouse cursor hide.
            onImmersiveChange = immersive,
            secondWindow = secondWindow,
            onSecondWindowChange = setSecondWindow,
            autoStart = autoStart,
        )
    }

    if (secondWindow) {
        val second = remember {
            CamGridViewModel(viewModel).apply {
                val page = SecondWindow.startPage(preferences, ViewPaging.pages(config.value))
                setGridPosition(GridPosition(page, 0))
            }
        }
        // Which view it shows, for the next start.
        LaunchedEffect(second) {
            snapshotFlow { ViewPaging.pages(second.config.value).getOrNull(second.gridPage) }
                .filterNotNull()
                .collect { SecondWindow.save(preferences, it) }
        }
        val defaultScreen = remember {
            mainWindow[0]?.let { main -> freeScreen(screenBounds(), main.bounds) }
        }
        CamGridWindow(
            WindowStateStore.forSecondWindow(),
            icon,
            onCloseRequest = { setSecondWindow(false) },
            title = "CamGrid 2",
            defaultScreen = defaultScreen,
        ) { immersive ->
            CamGridApp(
                viewModel = second,
                video = DesktopVideoPlatform,
                appVersion = DesktopAppVersion.current,
                onImmersiveChange = immersive,
            )
        }
    }
}

/**
 * One CamGrid window: its size, place and placement kept in [store], F11 for fullscreen, focus
 * rings while the keys are used, and the mouse cursor hidden on the camera wall while it rests.
 * [content] gets the callback that says when the camera wall (or a camera) is showing.
 */
@Composable
private fun ApplicationScope.CamGridWindow(
    store: WindowStateStore,
    icon: Painter?,
    onCloseRequest: () -> Unit,
    title: String = "CamGrid",
    defaultScreen: Rectangle? = null,
    content: @Composable FrameWindowScope.(onImmersiveChange: (Boolean) -> Unit) -> Unit,
) {
    val windowState = remember { initialWindowState(store.load(), defaultScreen) }
    val initialWindow = remember { windowState.toSaved(previous = null) }
    SaveWindowState(windowState, store, initialWindow)
    var immersive by remember { mutableStateOf(false) }
    // Focus rings only while the user moves around with the keys. Fed here as well as in
    // CamGridApp, so keys pressed in a dialog count too.
    val keyboardNavigation = remember { KeyboardNavigation() }
    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        title = title,
        icon = icon,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown) keyboardNavigation.onKeyDown(event.key)
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
            CompositionLocalProvider(
                LocalHasKeyboardAndMouse provides true,
                LocalKeyboardNavigation provides keyboardNavigation,
            ) {
                AutoHideCursor(enabled = immersive) { content { immersive = it } }
            }
        }
    }
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
