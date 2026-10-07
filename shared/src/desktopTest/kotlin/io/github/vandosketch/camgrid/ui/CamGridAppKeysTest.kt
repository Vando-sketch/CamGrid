package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.github.vandosketch.camgrid.CamGridViewModel
import io.github.vandosketch.camgrid.Screen
import io.github.vandosketch.camgrid.about.License
import io.github.vandosketch.camgrid.core.ConfigCodec
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.createCamGridHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The root composable: keys it handles (Back on every screen but the grid, the shortcuts help),
 * the focus rings with a keyboard and mouse, and the saved state of screens with sub-screens.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class CamGridAppKeysTest {

    // The view model's scope runs on Dispatchers.Main, which the JVM tests do not have.
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun ComposeUiTest.showApp(cameras: Int = 4, keyboardAndMouse: Boolean = false): CamGridViewModel {
        val viewModel = CamGridViewModel(
            configStore = MemoryConfigStore(ConfigCodec.encode(testConfig(cameras))),
            backupFiles = NoBackupFiles,
            go2rtcClient = Go2rtcClient(createCamGridHttpClient(MockEngine { respond("") })),
        )
        setContent {
            CompositionLocalProvider(LocalHasKeyboardAndMouse provides keyboardAndMouse) {
                CamGridTheme { CamGridApp(viewModel = viewModel, video = FakeVideoPlatform(), appVersion = "0.1.0-dev", onImmersiveChange = {}) }
            }
        }
        return viewModel
    }

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    /** The scroll position of the screen's (only) lazy list, as its semantics report it. */
    private fun ComposeUiTest.listScrollOffset(): Float =
        onNode(hasScrollToNodeAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun escapeLeavesFullscreen() = runComposeUiTest {
        val viewModel = showApp()
        viewModel.openCamera("cam3")
        waitForIdle()
        press(Key.Escape)
        assertEquals(Screen.Grid, viewModel.screen)
        // Back on the camera that was open.
        assertEquals(2, viewModel.gridFocusIndex)
    }

    @Test
    fun backspaceLeavesFullscreen() = runComposeUiTest {
        val viewModel = showApp()
        viewModel.openCamera("cam1")
        waitForIdle()
        press(Key.Backspace)
        assertEquals(Screen.Grid, viewModel.screen)
    }

    @Test
    fun escapeLeavesSettings() = runComposeUiTest {
        val viewModel = showApp()
        viewModel.openSettings()
        waitForIdle()
        press(Key.Escape)
        assertEquals(Screen.Grid, viewModel.screen)
    }

    @Test
    fun escapeWalksBackFromALicenseText() = runComposeUiTest {
        val viewModel = showApp()
        viewModel.openSettings()
        viewModel.openLicenses()
        viewModel.openLicense(License.APACHE_2_0)
        waitForIdle()
        press(Key.Escape)
        assertEquals(Screen.Licenses, viewModel.screen)
        press(Key.Escape)
        assertEquals(Screen.Settings, viewModel.screen)
    }

    @Test
    fun enterThenEscapeRoundTrip() = runComposeUiTest {
        val viewModel = showApp()
        press(Key.DirectionRight)
        press(Key.Enter)
        assertEquals(Screen.Fullscreen("cam2"), viewModel.screen)
        press(Key.Escape)
        assertEquals(Screen.Grid, viewModel.screen)
        // The grid takes the keys again.
        press(Key.DirectionDown)
        assertEquals(3, viewModel.gridFocusIndex)
    }

    @Test
    fun f1ShowsTheShortcutsFromGridAndFullscreen() = runComposeUiTest {
        val viewModel = showApp()
        press(Key.F1)
        onNodeWithText("Keyboard shortcuts").assertExists()
        // (Esc closes it through the window's back events, which the test host does not send.)
        onNodeWithText("Close").performClick()
        waitForIdle()
        onNodeWithText("Keyboard shortcuts").assertDoesNotExist()
        assertEquals(Screen.Grid, viewModel.screen)
        // The grid takes the keys again.
        press(Key.DirectionRight)
        assertEquals(1, viewModel.gridFocusIndex)

        viewModel.openCamera("cam1")
        waitForIdle()
        press(Key.F1)
        onNodeWithText("Keyboard shortcuts").assertExists()
    }

    @Test
    fun desktopShowsTheSelectionRingOnlyAfterKeys() = runComposeUiTest {
        val viewModel = showApp(keyboardAndMouse = true)
        // The grid has focus from the start, but nobody used the keys yet.
        assertFalse(focusRingVisible())
        press(Key.DirectionRight)
        assertEquals(1, viewModel.gridFocusIndex)
        assertTrue(focusRingVisible())
        // Moving the mouse hides it again; the selection stays.
        onRoot().performMouseInput {
            moveTo(Offset(20f, 200f))
            moveTo(Offset(40f, 200f))
            moveTo(Offset(60f, 200f))
        }
        assertFalse(focusRingVisible())
        assertEquals(1, viewModel.gridFocusIndex)
        press(Key.DirectionLeft)
        assertTrue(focusRingVisible())
    }

    @Test
    fun settingsKeepTheirScrollPositionAcrossTheLicenses() = runComposeUiTest {
        val viewModel = showApp(cameras = 12)
        viewModel.openSettings()
        waitForIdle()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Open-source licenses"))
        val scrolled = listScrollOffset()
        assertTrue(scrolled > 0f, "settings did not scroll: $scrolled")

        onNodeWithText("Open-source licenses").performClick()
        waitForIdle()
        assertEquals(Screen.Licenses, viewModel.screen)
        viewModel.back()
        waitForIdle()

        assertEquals(Screen.Settings, viewModel.screen)
        assertEquals(scrolled, listScrollOffset())
        onNodeWithText("Open-source licenses").assertIsDisplayed()
    }

    @Test
    fun settingsOpenAtTheTopAgainFromTheGrid() = runComposeUiTest {
        val viewModel = showApp(cameras = 12)
        viewModel.openSettings()
        waitForIdle()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Open-source licenses"))
        assertTrue(listScrollOffset() > 0f)
        viewModel.back()
        waitForIdle()
        assertEquals(Screen.Grid, viewModel.screen)

        viewModel.openSettings()
        waitForIdle()
        assertEquals(0f, listScrollOffset())
    }
}
