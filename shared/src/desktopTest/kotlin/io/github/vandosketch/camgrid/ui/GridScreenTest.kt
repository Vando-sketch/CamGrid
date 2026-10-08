package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.GridPosition
import io.github.vandosketch.camgrid.core.StreamType
import io.github.vandosketch.camgrid.platform.LiveStream
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.VideoPlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GridScreenTest {

    private class Harness {
        var position by mutableStateOf(GridPosition(0, 0))
        val opened = mutableListOf<String>()
        var settingsOpened = 0

        /** Keys the grid left unhandled, as the app around it gets them. */
        val keysPassedOn = mutableListOf<Key>()
    }

    /** Streams that never get past connecting; the surface is black, like the platforms' while there is no picture. */
    private class ConnectingVideoPlatform : VideoPlatform {
        override val supportedTypes = setOf(StreamType.RTSP, StreamType.WEBRTC)

        @Composable
        override fun rememberLiveStream(url: String, type: StreamType, label: String, audioEnabled: Boolean): LiveStream =
            remember(url) {
                object : LiveStream {
                    override val status: StreamStatus = StreamStatus.Connecting

                    override fun setMuted(muted: Boolean) {}

                    override fun release() {}
                }
            }

        @Composable
        override fun Surface(stream: LiveStream?, modifier: Modifier, fit: FitMode) {
            Box(modifier.background(Color.Black))
        }
    }

    private fun ComposeUiTest.showGrid(
        cameras: Int,
        start: GridPosition = GridPosition(0, 0),
        video: VideoPlatform = FakeVideoPlatform(),
    ): Harness {
        val harness = Harness().apply { position = start }
        setContent {
            CamGridTheme {
                Box(
                    Modifier.onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) harness.keysPassedOn += event.key
                        false
                    },
                ) {
                    GridScreen(
                        video = video,
                        config = testConfig(cameras),
                        page = harness.position.page,
                        focusIndex = harness.position.index,
                        onPositionChange = { harness.position = it },
                        onOpenCamera = { harness.opened += it },
                        onOpenSettings = { harness.settingsOpened++ },
                    )
                }
            }
        }
        return harness
    }

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun selectedTileIsExposedInSemantics() = runComposeUiTest {
        showGrid(cameras = 4, start = GridPosition(0, 1))
        onNodeWithText("Cam 2").assertIsSelected()
        onNodeWithText("Cam 1").assertIsNotSelected()
    }

    @Test
    fun arrowsMoveTheSelectionWithoutFocusingTiles() = runComposeUiTest {
        val harness = showGrid(cameras = 4)
        press(Key.DirectionRight)
        assertEquals(GridPosition(0, 1), harness.position)
        onNodeWithText("Cam 2").assertIsSelected()
        press(Key.DirectionDown)
        assertEquals(GridPosition(0, 3), harness.position)
        onNodeWithText("Cam 4").assertIsSelected()
        // The grid container keeps focus; tiles never take it.
        onNodeWithText("Cam 4").assert(!isFocused())
        assertEquals(1, onAllNodes(isFocused()).fetchSemanticsNodes().size)
    }

    @Test
    fun rightPastTheEdgeSwitchesPage() = runComposeUiTest {
        val harness = showGrid(cameras = 6, start = GridPosition(0, 1))
        press(Key.DirectionRight)
        assertEquals(GridPosition(1, 0), harness.position)
        onNodeWithText("Cam 5").assertIsSelected()
    }

    @Test
    fun enterOpensTheSelectedCamera() = runComposeUiTest {
        val harness = showGrid(cameras = 4, start = GridPosition(0, 2))
        press(Key.Enter)
        assertEquals(listOf("cam3"), harness.opened)
    }

    @Test
    fun dpadCenterOpensTheSelectedCamera() = runComposeUiTest {
        val harness = showGrid(cameras = 4, start = GridPosition(0, 1))
        press(Key.DirectionCenter)
        assertEquals(listOf("cam2"), harness.opened)
    }

    @Test
    fun digitsOpenTheNthCameraOfThePage() = runComposeUiTest {
        val harness = showGrid(cameras = 6, start = GridPosition(1, 0))
        press(Key.Two)
        assertEquals(listOf("cam6"), harness.opened)
        // Page 2 has only two cameras.
        press(Key.Three)
        assertEquals(listOf("cam6"), harness.opened)
    }

    @Test
    fun pageKeysSwitchPage() = runComposeUiTest {
        val harness = showGrid(cameras = 6)
        press(Key.PageDown)
        assertEquals(1, harness.position.page)
        onNodeWithText("Cam 5").assertIsSelected()
        press(Key.PageDown)
        assertEquals(1, harness.position.page)
        press(Key.PageUp)
        assertEquals(0, harness.position.page)
        press(Key.ChannelUp)
        assertEquals(1, harness.position.page)
        press(Key.MediaPrevious)
        assertEquals(0, harness.position.page)
    }

    @Test
    fun upFromTheTopRowGoesToSettingsAndDownComesBack() = runComposeUiTest {
        val harness = showGrid(cameras = 4, start = GridPosition(0, 1))
        press(Key.DirectionUp)
        press(Key.Enter)
        assertEquals(1, harness.settingsOpened)
        assertEquals(emptyList(), harness.opened)
        press(Key.DirectionDown)
        press(Key.Enter)
        assertEquals(listOf("cam2"), harness.opened)
    }

    @Test
    fun clickingATileOpensIt() = runComposeUiTest {
        val harness = showGrid(cameras = 4)
        onNodeWithText("Cam 3").performClick()
        assertEquals(listOf("cam3"), harness.opened)
    }

    @Test
    fun pageIndicatorFadesOutAndComesBackOnAPageSwitch() = runComposeUiTest {
        showGrid(cameras = 6)
        onNodeWithText("1 / 2").assertExists()
        // Then it gets out of the way of the tile beneath it.
        mainClock.advanceTimeBy(PAGE_INDICATOR_MS + 1_000)
        waitForIdle()
        onNodeWithText("1 / 2").assertDoesNotExist()
        press(Key.PageDown)
        onNodeWithText("2 / 2").assertExists()
        mainClock.advanceTimeBy(PAGE_INDICATOR_MS + 1_000)
        waitForIdle()
        onNodeWithText("2 / 2").assertDoesNotExist()
    }

    /** Lets the grid sit untouched until the selection ring has faded out. */
    private fun ComposeUiTest.leaveAlone() {
        mainClock.advanceTimeBy(SELECTION_IDLE_MS + RING_FADE_MS + 100)
        waitForIdle()
    }

    @Test
    fun theSelectionRingFadesOutWhenNobodyPressesAKey() = runComposeUiTest {
        val harness = showGrid(cameras = 4)
        assertTrue(focusRingVisible())
        mainClock.advanceTimeBy(SELECTION_IDLE_MS - 1_000)
        assertTrue(focusRingVisible(), "gone before the timeout")
        leaveAlone()
        assertFalse(focusRingVisible())
        // Only hidden: the selection itself stays.
        onNodeWithText("Cam 1").assertIsSelected()
        assertEquals(GridPosition(0, 0), harness.position)
    }

    @Test
    fun theFirstArrowWhileHiddenOnlyShowsTheRing() = runComposeUiTest {
        val harness = showGrid(cameras = 4)
        leaveAlone()
        press(Key.DirectionRight)
        assertEquals(GridPosition(0, 0), harness.position)
        assertTrue(focusRingVisible())
        // Now it moves, as usual.
        press(Key.DirectionRight)
        assertEquals(GridPosition(0, 1), harness.position)
    }

    @Test
    fun theFirstOkWhileHiddenOpensNothing() = runComposeUiTest {
        val harness = showGrid(cameras = 4, start = GridPosition(0, 2))
        for (ok in listOf(Key.DirectionCenter, Key.Enter)) {
            leaveAlone()
            press(ok)
            assertEquals(emptyList(), harness.opened, "$ok")
            assertTrue(focusRingVisible())
        }
        press(Key.Enter)
        assertEquals(listOf("cam3"), harness.opened)
    }

    @Test
    fun aKeyRestartsTheTimeout() = runComposeUiTest {
        val harness = showGrid(cameras = 4)
        mainClock.advanceTimeBy(SELECTION_IDLE_MS - 2_000)
        press(Key.DirectionRight)
        assertEquals(GridPosition(0, 1), harness.position)
        // Past the first timeout, but not yet a whole timeout after the key.
        mainClock.advanceTimeBy(SELECTION_IDLE_MS - 2_000)
        assertTrue(focusRingVisible())
        leaveAlone()
        assertFalse(focusRingVisible())
    }

    @Test
    fun backStillReachesTheAppWhileTheRingIsHidden() = runComposeUiTest {
        val harness = showGrid(cameras = 4)
        leaveAlone()
        press(Key.Back)
        assertEquals(listOf(Key.Back), harness.keysPassedOn)
        harness.keysPassedOn.clear()
        leaveAlone()
        press(Key.Escape)
        assertEquals(listOf(Key.Escape), harness.keysPassedOn)
    }

    @Test
    fun digitsStillOpenTheirCameraWhileTheRingIsHidden() = runComposeUiTest {
        // A digit names its camera; it does not depend on where the hidden selection is.
        val harness = showGrid(cameras = 4)
        leaveAlone()
        press(Key.Three)
        assertEquals(listOf("cam3"), harness.opened)
    }

    @Test
    fun aTapStillOpensTheCameraWhileTheRingIsHidden() = runComposeUiTest {
        val harness = showGrid(cameras = 4)
        leaveAlone()
        onNodeWithText("Cam 2").performClick()
        assertEquals(listOf("cam2"), harness.opened)
    }

    @Test
    fun theSettingsButtonRingFadesTooAndTheFirstOkOnlyShowsIt() = runComposeUiTest {
        val harness = showGrid(cameras = 4)
        press(Key.DirectionUp)
        // The gear has the focus and its ring; the tile ring is gone with the focus.
        assertTrue(focusRingVisible())
        leaveAlone()
        assertFalse(focusRingVisible())
        press(Key.Enter)
        assertEquals(0, harness.settingsOpened)
        assertTrue(focusRingVisible())
        press(Key.Enter)
        assertEquals(1, harness.settingsOpened)
    }

    @Test
    fun connectingTilesShowAPlaceholderWithGapsBetweenThem() = runComposeUiTest {
        showGrid(cameras = 4, video = ConnectingVideoPlatform())
        onAllNodesWithText("Connecting…", useUnmergedTree = true).assertCountEquals(4)
        // Top centre of a tile (clear of its name and status): the placeholder, not black.
        val tile = onNodeWithText("Cam 1").captureToImage()
        assertColor(Color(0xFF1E2329), tile.pixel(tile.width / 2, tile.height / 8))
        // Between the two top tiles: the black gap, so the tiles stand apart.
        val grid = onRoot().captureToImage()
        assertColor(Color.Black, grid.pixel(grid.width / 2, grid.height / 4))
    }

    @Test
    fun playingTilesShowTheVideoWithoutThePlaceholder() = runComposeUiTest {
        // FakeVideoPlatform plays at once and draws dark grey.
        showGrid(cameras = 4)
        val tile = onNodeWithText("Cam 1").captureToImage()
        assertColor(Color.DarkGray, tile.pixel(tile.width / 2, tile.height / 8))
    }

    @Test
    fun aDarkScrimKeepsTheNameReadableWithoutCoveringTheVideo() = runComposeUiTest {
        // FakeVideoPlatform plays dark grey; Cam 2 is not selected, so no border at its edge.
        showGrid(cameras = 4)
        onNodeWithText("Cam 2").assertExists()
        val tile = onNodeWithText("Cam 2").captureToImage()
        // Middle of the tile: the video as it is.
        assertColor(Color.DarkGray, tile.pixel(tile.width / 2, tile.height / 2))
        // Bottom right, clear of the name: darker than the video, but not covered black.
        val bottom = tile.pixel(tile.width * 3 / 4, tile.height - 4)
        assertTrue(bottom.red < Color.DarkGray.red * 0.75f, "no scrim at the bottom: $bottom")
        assertTrue(bottom.red > 0.02f, "the scrim hides the video: $bottom")
    }

    private fun ImageBitmap.pixel(x: Int, y: Int): Color = toPixelMap()[x, y]

    private fun assertColor(expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < 0.03f &&
            abs(expected.green - actual.green) < 0.03f &&
            abs(expected.blue - actual.blue) < 0.03f
        assertTrue(close, "expected $expected, was $actual")
    }
}
