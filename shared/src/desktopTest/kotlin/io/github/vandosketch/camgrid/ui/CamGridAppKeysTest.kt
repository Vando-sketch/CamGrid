package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.github.vandosketch.camgrid.CamGridViewModel
import io.github.vandosketch.camgrid.Screen
import io.github.vandosketch.camgrid.core.ConfigCodec
import io.github.vandosketch.camgrid.data.Go2rtcClient
import io.github.vandosketch.camgrid.data.createCamGridHttpClient
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.platform.BackupPickers
import io.github.vandosketch.camgrid.platform.ConfigStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Keys handled by the root composable: Back on every screen but the grid, the shortcuts help. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class CamGridAppKeysTest {

    // The view model's scope runs on Dispatchers.Main, which the JVM tests do not have.
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class MemoryStore(var json: String?) : ConfigStore {
        override fun read(): String? = json

        override fun write(json: String) {
            this.json = json
        }
    }

    private object NoBackupFiles : BackupFiles {
        override val folderPath: String? = null
        override fun suggestedName() = "camgrid-backup.json"
        override fun listFolder() = emptyList<String>()
        override fun writeToFolder(text: String) = error("unused")
        override fun readFromFolder(name: String) = error("unused")

        @Composable
        override fun rememberPickers(
            onSaveChosen: (BackupDocument?) -> Unit,
            onOpenChosen: (BackupDocument?) -> Unit,
        ): BackupPickers = object : BackupPickers {
            override fun launchSave(suggestedName: String) = false
            override fun launchOpen() = false
        }
    }

    private fun ComposeUiTest.showApp(): CamGridViewModel {
        val viewModel = CamGridViewModel(
            configStore = MemoryStore(ConfigCodec.encode(testConfig(4))),
            backupFiles = NoBackupFiles,
            go2rtcClient = Go2rtcClient(createCamGridHttpClient(MockEngine { respond("") })),
        )
        setContent {
            CamGridTheme { CamGridApp(viewModel = viewModel, video = FakeVideoPlatform(), onImmersiveChange = {}) }
        }
        return viewModel
    }

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

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
}
