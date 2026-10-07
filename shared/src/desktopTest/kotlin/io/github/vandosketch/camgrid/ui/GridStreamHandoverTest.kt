package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.runComposeUiTest
import io.github.vandosketch.camgrid.CamGridViewModel
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.ConfigCodec
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.createCamGridHttpClient
import io.github.vandosketch.camgrid.platform.LiveStream
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.VideoPlatform
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Issue #29: the camera opened in fullscreen keeps its grid stream, which fullscreen shows until
 * its own stream plays and the grid has back at once; the other tiles' streams close before the
 * fullscreen stream opens (a Fire TV decodes only about four at once).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class GridStreamHandoverTest {

    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    /** A stream that records its URL; grid streams play at once, fullscreen ones connect. */
    private class RecordedStream(val url: String, audioEnabled: Boolean) : LiveStream {
        override var status: StreamStatus by mutableStateOf(if (audioEnabled) StreamStatus.Connecting else StreamStatus.Playing)
        var released = false

        override fun setMuted(muted: Boolean) {}

        override fun release() {
            released = true
        }
    }

    /** Logs every stream opened and closed, in order; each surface is tagged with its stream's URL. */
    private class RecordingVideoPlatform : VideoPlatform {
        val events = mutableListOf<String>()
        val open = mutableMapOf<String, RecordedStream>()

        override val supportedTypes = setOf(StreamType.RTSP, StreamType.WEBRTC)
        override val supportsZoom = true

        @Composable
        override fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream {
            val stream = remember(url, audioEnabled) { RecordedStream(url, audioEnabled) }
            DisposableEffect(stream) {
                events += "open $url"
                open[url] = stream
                onDispose {
                    events += "close $url"
                    open.remove(url)
                    stream.release()
                }
            }
            return stream
        }

        @Composable
        override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode) {
            Box(modifier.testTag("video ${(stream as? RecordedStream)?.url}"))
        }
    }

    private fun grid(n: Int) = "rtsp://192.0.2.1:8554/cam$n"
    private fun hd(n: Int) = "rtsp://192.0.2.1:8554/cam$n-hd"

    private fun config(count: Int = 4) = CamGridConfig(
        views = listOf(CamView.uniform("main", "", 2, 2)),
        cameras = (1..count).map { Camera(id = "cam$it", name = "Cam $it", gridUrl = grid(it), detailUrl = hd(it)) },
    )

    private fun ComposeUiTest.showApp(video: VideoPlatform, config: CamGridConfig = config()): CamGridViewModel {
        val viewModel = CamGridViewModel(
            configStore = MemoryConfigStore(ConfigCodec.encode(config)),
            backupFiles = NoBackupFiles,
            go2rtcClient = Go2rtcClient(createCamGridHttpClient(MockEngine { respond("") })),
        )
        setContent {
            CamGridTheme { CamGridApp(viewModel = viewModel, video = video, appVersion = "0.1.0-dev", onImmersiveChange = {}) }
        }
        waitForIdle()
        return viewModel
    }

    private fun ComposeUiTest.assertVideoShown(url: String, shown: Boolean = true) {
        // Unmerged: a clickable grid tile merges its children's semantics into its own.
        val count = onAllNodes(hasTestTag("video $url"), useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(shown, count > 0, "video $url shown")
    }

    @Test
    fun openingACameraKeepsItsGridStream() = runComposeUiTest {
        val video = RecordingVideoPlatform()
        val viewModel = showApp(video)
        val tileStream = video.open.getValue(grid(1))

        viewModel.openCamera("cam1")
        waitForIdle()

        assertSame(tileStream, video.open[grid(1)])
        assertFalse(tileStream.released)
        assertEquals(1, video.events.count { it == "open ${grid(1)}" })
        // The other tiles are closed, the fullscreen stream is open.
        assertEquals(setOf(grid(1), hd(1)), video.open.keys)
    }

    @Test
    fun otherTilesCloseBeforeTheFullscreenStreamOpens() = runComposeUiTest {
        val video = RecordingVideoPlatform()
        val viewModel = showApp(video)
        video.events.clear()

        viewModel.openCamera("cam1")
        waitForIdle()

        val openHd = video.events.indexOf("open ${hd(1)}")
        assertTrue(openHd >= 0)
        for (n in 2..4) {
            val close = video.events.indexOf("close ${grid(n)}")
            assertTrue(close in 0 until openHd, "cam$n closed before the fullscreen stream opened: ${video.events}")
        }
    }

    @Test
    fun fullscreenShowsTheGridStreamUntilItsOwnStreamPlays() = runComposeUiTest {
        val video = RecordingVideoPlatform()
        val viewModel = showApp(video)

        viewModel.openCamera("cam1")
        waitForIdle()
        assertVideoShown(grid(1))
        // Its own stream is drawn from the start, so it can start playing underneath.
        assertVideoShown(hd(1))

        runOnIdle { video.open.getValue(hd(1)).status = StreamStatus.Playing }
        waitForIdle()
        assertVideoShown(grid(1), shown = false)
        assertVideoShown(hd(1))
    }

    @Test
    fun backToTheGridKeepsTheSameStream() = runComposeUiTest {
        val video = RecordingVideoPlatform()
        val viewModel = showApp(video)
        val tileStream = video.open.getValue(grid(1))

        viewModel.openCamera("cam1")
        waitForIdle()
        viewModel.back()
        waitForIdle()

        assertSame(tileStream, video.open[grid(1)])
        assertEquals(1, video.events.count { it == "open ${grid(1)}" })
        assertFalse(hd(1) in video.open)
        assertEquals((1..4).map(::grid).toSet(), video.open.keys)
        assertVideoShown(grid(1))
    }

    @Test
    fun switchingCameraInFullscreenMovesToTheOtherGridStream() = runComposeUiTest {
        val video = RecordingVideoPlatform()
        val viewModel = showApp(video)

        viewModel.openCamera("cam1")
        waitForIdle()
        viewModel.openCamera("cam2")
        waitForIdle()

        assertEquals(setOf(grid(2), hd(2)), video.open.keys)
    }

    @Test
    fun settingsCloseEveryStream() = runComposeUiTest {
        val video = RecordingVideoPlatform()
        val viewModel = showApp(video)

        viewModel.openSettings()
        waitForIdle()

        assertTrue(video.open.isEmpty(), "still open: ${video.open.keys}")
    }

    @Test
    fun aCameraShownTwiceOnAPageGetsTwoStreams() = runComposeUiTest {
        // Two fixed tiles with the same camera: the first shares the kept stream, the second
        // has its own, as a stream draws into one view at a time.
        val base = config(2)
        val view = base.views.single()
        val twice = base.copy(
            views = listOf(view.copy(tiles = view.tiles.mapIndexed { i, tile -> if (i < 2) tile.copy(camera = "cam1") else tile })),
        )
        val video = RecordingVideoPlatform()
        showApp(video, twice)

        assertEquals(2, video.events.count { it == "open ${grid(1)}" })
    }
}
