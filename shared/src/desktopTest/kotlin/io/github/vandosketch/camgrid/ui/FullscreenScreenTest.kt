package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FullscreenScreenTest {

    private class Harness {
        var cameraId by mutableStateOf("cam1")
        var closed = 0
    }

    private fun ComposeUiTest.showFullscreen(
        video: FakeVideoPlatform = FakeVideoPlatform(),
        keyboardAndMouse: Boolean = false,
    ): Harness {
        val harness = Harness()
        val cameras = testConfig(5).cameras
        setContent {
            CompositionLocalProvider(LocalHasKeyboardAndMouse provides keyboardAndMouse) {
                CamGridTheme {
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
}
