package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.CamView
import kotlin.test.Test
import kotlin.test.assertTrue

/** The order buttons of the views and cameras lists, and scrolling the whole screen. */
@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {

    private val threeViews = listOf(
        CamView.uniform("a", "First", 2, 2),
        CamView.uniform("b", "Second", 2, 2),
        CamView.uniform("c", "Third", 2, 2),
    )

    /** Moves the item at [index] by [delta] places, like the ViewModel does. */
    private fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
        val target = (index + delta).coerceIn(0, lastIndex)
        return toMutableList().apply { add(target, removeAt(index)) }
    }

    private fun ComposeUiTest.showSettings(initial: CamGridConfig, modifier: Modifier = Modifier) {
        setContent {
            var config by remember { mutableStateOf(initial) }
            CamGridTheme {
                Box(modifier) {
                    SettingsScreen(
                        config = config,
                        onDone = {},
                        onEditView = {},
                        onAddView = {},
                        onMoveView = { id, delta ->
                            config = config.copy(views = config.views.moved(config.views.indexOfFirst { it.id == id }, delta))
                        },
                        onMoveCamera = { id, delta ->
                            config = config.copy(cameras = config.cameras.moved(config.cameras.indexOfFirst { it.id == id }, delta))
                        },
                        onEditCamera = {},
                        onDeleteCamera = {},
                        onImport = {},
                        onBackup = {},
                        appVersion = "0.1.0",
                        onOpenLicenses = {},
                    )
                }
            }
        }
    }

    /** The order button with [description], scrolled into view first (the list is lazy). */
    private fun ComposeUiTest.button(description: String): SemanticsNodeInteraction {
        onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasContentDescription(description))
        return onNodeWithContentDescription(description)
    }

    private fun ComposeUiTest.focusAndClick(description: String) {
        button(description).requestFocus()
        waitForIdle()
        button(description).performClick()
        waitForIdle()
    }

    @Test
    fun theEndsOfTheCameraListCannotMoveFurther() = runComposeUiTest {
        showSettings(testConfig(3))
        button("Move Cam 1 up").assertIsNotEnabled()
        button("Move Cam 1 down").assertIsEnabled()
        button("Move Cam 2 up").assertIsEnabled()
        button("Move Cam 2 down").assertIsEnabled()
        button("Move Cam 3 up").assertIsEnabled()
        button("Move Cam 3 down").assertIsNotEnabled()
    }

    @Test
    fun theEndsOfTheViewListCannotMoveFurther() = runComposeUiTest {
        showSettings(CamGridConfig(views = threeViews))
        button("Move First up").assertIsNotEnabled()
        button("Move First down").assertIsEnabled()
        button("Move Third up").assertIsEnabled()
        button("Move Third down").assertIsNotEnabled()
    }

    @Test
    fun aSingleCameraHasNoOrderButtonsToPress() = runComposeUiTest {
        showSettings(testConfig(1))
        button("Move Cam 1 up").assertIsNotEnabled()
        button("Move Cam 1 down").assertIsNotEnabled()
    }

    @Test
    fun movingACameraToTheTopKeepsTheFocusInItsRow() = runComposeUiTest {
        showSettings(testConfig(3))
        focusAndClick("Move Cam 2 up")
        // Cam 2 is first now: its Up button is disabled, so the focus went to its Down button.
        button("Move Cam 2 up").assertIsNotEnabled()
        button("Move Cam 2 down").assertIsFocused()
        button("Move Cam 1 up").assertIsEnabled()
    }

    @Test
    fun movingACameraToTheBottomKeepsTheFocusInItsRow() = runComposeUiTest {
        showSettings(testConfig(3))
        focusAndClick("Move Cam 2 down")
        button("Move Cam 2 down").assertIsNotEnabled()
        button("Move Cam 2 up").assertIsFocused()
        button("Move Cam 3 down").assertIsEnabled()
    }

    @Test
    fun movingAViewToTheTopKeepsTheFocusInItsRow() = runComposeUiTest {
        showSettings(CamGridConfig(views = threeViews))
        focusAndClick("Move Second up")
        button("Move Second up").assertIsNotEnabled()
        button("Move Second down").assertIsFocused()
    }

    @Test
    fun aLongListShowsAScrollbarAndReachesTheEnd() = runComposeUiTest {
        showSettings(testConfig(20), Modifier.size(width = 1024.dp, height = 720.dp))
        waitForIdle()
        assertTrue(onAllNodesWithTag(SCROLLBAR_TAG).fetchSemanticsNodes().isNotEmpty(), "no scrollbar")
        onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText("Open-source licenses"))
        onNodeWithText("Open-source licenses").assertIsDisplayed()
    }

    @Test
    fun comingBackFromASubScreenFocusesTheButtonThatOpenedIt() = runComposeUiTest {
        var onSettings by mutableStateOf(true)
        setContent {
            val holder = rememberSaveableStateHolder()
            CamGridTheme {
                Box(Modifier.size(width = 1024.dp, height = 720.dp)) {
                    if (onSettings) {
                        holder.SaveableStateProvider("settings") {
                            SettingsScreen(
                                config = testConfig(20),
                                onDone = {},
                                onEditView = {},
                                onAddView = {},
                                onMoveView = { _, _ -> },
                                onMoveCamera = { _, _ -> },
                                onEditCamera = {},
                                onDeleteCamera = {},
                                onImport = {},
                                onBackup = {},
                                appVersion = "0.1.0",
                                onOpenLicenses = { onSettings = false },
                            )
                        }
                    }
                }
            }
        }
        onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText("Open-source licenses"))
        onNodeWithText("Open-source licenses").requestFocus()
        // OK on the remote (Enter here) keeps key mode, where the focus is given back.
        onNodeWithText("Open-source licenses").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        runOnIdle { onSettings = true }
        waitForIdle()
        // Done is scrolled out of view; the Licenses button has the focus again.
        onNodeWithText("Open-source licenses").assertIsFocused()
    }
}
