package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.platform.VideoPlatform
import io.github.vandosketch.camgrid.platform.VideoZoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FullscreenScreenTest {

    private class Harness {
        var cameraId by mutableStateOf("cam1")
        var closed = 0

        /** Back that reached the app (CamGridApp leaves fullscreen then). */
        var appBacks = 0

        /** Sends Back like the Back key or Esc do. */
        lateinit var back: () -> Unit
    }

    private fun ComposeUiTest.showFullscreen(
        video: VideoPlatform = FakeVideoPlatform(),
        keyboardAndMouse: Boolean = false,
    ): Harness {
        val harness = Harness()
        val cameras = testConfig(5).cameras
        setContent {
            CompositionLocalProvider(LocalHasKeyboardAndMouse provides keyboardAndMouse) {
                CamGridTheme {
                    // Like CamGridApp: its Back handler comes before the screen's.
                    BackHandler { harness.appBacks++ }
                    harness.back = rememberBackDispatcher()
                    FullscreenScreen(
                        video = video,
                        cameras = cameras,
                        cameraId = harness.cameraId,
                        onSwitchCamera = { harness.cameraId = it },
                        onClose = { harness.closed++ },
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
    fun digitsSwitchCamera() = runComposeUiTest {
        val harness = showFullscreen()
        press(Key.Four)
        assertEquals("cam4", harness.cameraId)
        press(Key.NumPad2)
        assertEquals("cam2", harness.cameraId)
        // There is no ninth camera.
        press(Key.Nine)
        assertEquals("cam2", harness.cameraId)
    }

    @Test
    fun arrowsStillSwitchCamera() = runComposeUiTest {
        val harness = showFullscreen()
        press(Key.DirectionLeft)
        assertEquals("cam5", harness.cameraId)
    }

    @Test
    fun mSpaceAndOkToggleSound() = runComposeUiTest {
        val video = FakeVideoPlatform()
        showFullscreen(video)
        assertEquals(false, video.lastMuted)
        press(Key.M)
        assertEquals(true, video.lastMuted)
        press(Key.Spacebar)
        assertEquals(false, video.lastMuted)
        press(Key.Enter)
        assertEquals(true, video.lastMuted)
    }

    @Test
    fun closeButtonCloses() = runComposeUiTest {
        val harness = showFullscreen()
        onNodeWithContentDescription("Close").performClick()
        assertEquals(1, harness.closed)
    }

    @Test
    fun desktopShowsTheKeyboardHint() = runComposeUiTest {
        showFullscreen(keyboardAndMouse = true)
        onNodeWithText("← → or 1–9: switch camera · M: sound on/off · Esc: grid · ?: all shortcuts").assertExists()
    }

    @Test
    fun remoteHintWithoutKeyboard() = runComposeUiTest {
        showFullscreen(keyboardAndMouse = false)
        onNodeWithText("◀ ▶ or swipe: switch camera · OK: sound on/off · Back: grid").assertExists()
    }

    @Test
    fun soundAndCloseButtonsSitAtTheRightEdge() = runComposeUiTest {
        showFullscreen()
        val rootRight = onRoot().getUnclippedBoundsInRoot().right
        val close = onNodeWithContentDescription("Close").getUnclippedBoundsInRoot()
        val sound = onNodeWithContentDescription("Mute").getUnclippedBoundsInRoot()
        // Only the bar's 16 dp padding (and the button's touch margin) to the right of the close
        // button, the sound button right next to it. Before issue #25 both sat at about 60%.
        assertTrue(close.right >= rootRight - 32.dp, "close button ends at ${close.right}, the window at $rootRight")
        assertTrue(sound.right <= close.left && sound.right >= close.left - 16.dp, "sound $sound, close $close")
    }

    @Test
    fun soundButtonSaysWhatItDoes() = runComposeUiTest {
        val video = ZoomingFakeVideoPlatform()
        showFullscreen(video)
        onNodeWithContentDescription("Mute").performClick()
        waitForIdle()
        assertEquals(true, video.lastMuted)
        onNodeWithContentDescription("Unmute").performClick()
        waitForIdle()
        assertEquals(false, video.lastMuted)
        onNodeWithContentDescription("Mute").assertExists()
    }

    @Test
    fun noSoundButtonForAStreamWithoutAudio() = runComposeUiTest {
        showFullscreen(ZoomingFakeVideoPlatform(hasAudio = false))
        onNodeWithContentDescription("Mute").assertDoesNotExist()
        onNodeWithContentDescription("Unmute").assertDoesNotExist()
        onNodeWithContentDescription("Close").assertExists()
    }

    @Test
    fun soundButtonForAStreamWithAudio() = runComposeUiTest {
        showFullscreen(ZoomingFakeVideoPlatform(hasAudio = true))
        onNodeWithContentDescription("Mute").assertExists()
    }

    @Test
    fun fastForwardAndRewindZoom() = runComposeUiTest {
        val video = ZoomingFakeVideoPlatform()
        showFullscreen(video)
        press(Key.MediaFastForward)
        assertEquals(VideoZoom.STEP, video.lastZoom.scale)
        press(Key.MediaFastForward)
        assertTrue(video.lastZoom.scale > VideoZoom.STEP)
        press(Key.MediaRewind)
        press(Key.MediaRewind)
        assertEquals(VideoZoom.None, video.lastZoom)
    }

    @Test
    fun arrowsPanInsteadOfSwitchingWhileZoomed() = runComposeUiTest {
        val video = ZoomingFakeVideoPlatform()
        val harness = showFullscreen(video)
        press(Key.MediaFastForward)
        press(Key.DirectionLeft)
        assertEquals("cam1", harness.cameraId)
        // Left shows more of the left side: the picture moves right.
        assertTrue(video.lastZoom.offsetX > 0f, "${video.lastZoom}")
        press(Key.DirectionDown)
        assertTrue(video.lastZoom.offsetY < 0f, "${video.lastZoom}")
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        assertEquals("cam1", harness.cameraId)
        assertTrue(video.lastZoom.offsetX < 0f, "${video.lastZoom}")
    }

    @Test
    fun keyboardZoomsWithPlusMinusAndZero() = runComposeUiTest {
        val video = ZoomingFakeVideoPlatform()
        showFullscreen(video, keyboardAndMouse = true)
        press(Key.Equals)
        assertEquals(VideoZoom.STEP, video.lastZoom.scale)
        press(Key.Minus)
        assertEquals(VideoZoom.None, video.lastZoom)
        press(Key.NumPadAdd)
        press(Key.Plus)
        assertTrue(video.lastZoom.isZoomed)
        press(Key.Zero)
        assertEquals(VideoZoom.None, video.lastZoom)
    }

    @Test
    fun backResetsTheZoomBeforeLeaving() = runComposeUiTest {
        val video = ZoomingFakeVideoPlatform()
        val harness = showFullscreen(video)
        press(Key.MediaFastForward)
        runOnIdle { harness.back() }
        waitForIdle()
        assertEquals(VideoZoom.None, video.lastZoom)
        assertEquals(0, harness.appBacks)
        runOnIdle { harness.back() }
        assertEquals(1, harness.appBacks)
    }

    @Test
    fun switchingCameraResetsTheZoom() = runComposeUiTest {
        val video = ZoomingFakeVideoPlatform()
        val harness = showFullscreen(video)
        press(Key.MediaFastForward)
        press(Key.Four)
        assertEquals("cam4", harness.cameraId)
        assertEquals(VideoZoom.None, video.lastZoom)
    }

    @Test
    fun noZoomOnAPlatformWithoutIt() = runComposeUiTest {
        val video = ZoomingFakeVideoPlatform(supportsZoom = false)
        val harness = showFullscreen(video)
        press(Key.MediaFastForward)
        press(Key.Equals)
        assertEquals(VideoZoom.None, video.lastZoom)
        onNodeWithText("⏪ ⏩ or pinch: zoom").assertDoesNotExist()
        press(Key.DirectionLeft)
        assertEquals("cam5", harness.cameraId)
    }

    @Test
    fun pinchZoomsAndASwipeThenPans() = runComposeUiTest {
        val video = ZoomingFakeVideoPlatform()
        val harness = showFullscreen(video)
        onRoot().performTouchInput {
            pinch(
                start0 = center - Offset(20f, 0f),
                end0 = center - Offset(200f, 0f),
                start1 = center + Offset(20f, 0f),
                end1 = center + Offset(200f, 0f),
            )
        }
        waitForIdle()
        assertTrue(video.lastZoom.isZoomed, "${video.lastZoom}")
        val before = video.lastZoom.offsetX
        onRoot().performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals("cam1", harness.cameraId)
        assertTrue(video.lastZoom.offsetX < before, "${video.lastZoom}")
    }

    @Test
    fun doubleTapZoomsInAndOut() = runComposeUiTest {
        val video = ZoomingFakeVideoPlatform()
        showFullscreen(video)
        onRoot().performTouchInput { doubleClick(center) }
        waitForIdle()
        assertEquals(2f, video.lastZoom.scale)
        onRoot().performTouchInput { doubleClick(center) }
        waitForIdle()
        assertEquals(VideoZoom.None, video.lastZoom)
    }

    @Test
    fun swipeSwitchesCameraWhenNotZoomed() = runComposeUiTest {
        val harness = showFullscreen(ZoomingFakeVideoPlatform())
        onRoot().performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals("cam2", harness.cameraId)
    }

    @Test
    fun mouseWheelZooms() = runComposeUiTest {
        val video = ZoomingFakeVideoPlatform()
        showFullscreen(video, keyboardAndMouse = true)
        onRoot().performMouseInput {
            moveTo(center)
            scroll(-1f)
        }
        waitForIdle()
        assertTrue(video.lastZoom.isZoomed, "${video.lastZoom}")
        onRoot().performMouseInput { scroll(5f) }
        waitForIdle()
        assertEquals(VideoZoom.None, video.lastZoom)
    }

    @Test
    fun remoteHintMentionsZoom() = runComposeUiTest {
        showFullscreen(ZoomingFakeVideoPlatform(), keyboardAndMouse = false)
        onNodeWithText("◀ ▶ or swipe: switch camera · OK: sound on/off · Back: grid").assertExists()
        onNodeWithText("⏪ ⏩ or pinch: zoom").assertExists()
        press(Key.MediaFastForward)
        onNodeWithText("Zoom 1.5× · ◀ ▲ ▼ ▶ or drag: move · Back: whole picture").assertExists()
    }

    @Test
    fun keyboardHintMentionsZoom() = runComposeUiTest {
        showFullscreen(ZoomingFakeVideoPlatform(), keyboardAndMouse = true)
        onNodeWithText("+ − or mouse wheel: zoom").assertExists()
        press(Key.Equals)
        onNodeWithText("Zoom 1.5× · arrows or drag: move · 0 or Esc: whole picture").assertExists()
    }
}
