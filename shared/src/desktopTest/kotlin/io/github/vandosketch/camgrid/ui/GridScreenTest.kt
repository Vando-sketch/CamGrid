package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.github.vandosketch.camgrid.core.GridPosition
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GridScreenTest {

    private class Harness {
        var position by mutableStateOf(GridPosition(0, 0))
        val opened = mutableListOf<String>()
        var settingsOpened = 0
    }

    private fun ComposeUiTest.showGrid(cameras: Int, start: GridPosition = GridPosition(0, 0)): Harness {
        val harness = Harness().apply { position = start }
        setContent {
            CamGridTheme {
                GridScreen(
                    video = FakeVideoPlatform(),
                    config = testConfig(cameras),
                    page = harness.position.page,
                    focusIndex = harness.position.index,
                    onPositionChange = { harness.position = it },
                    onOpenCamera = { harness.opened += it },
                    onOpenSettings = { harness.settingsOpened++ },
                )
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
}
