package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
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
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.ReturnToGrid
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.platform.AutoStart
import io.github.vandosketch.camgrid.platform.AutoStartBlocker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The order of the sections, the order buttons of the views and cameras lists, start on boot,
 * and scrolling the whole screen.
 */
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

    private fun ComposeUiTest.showSettings(
        initial: CamGridConfig,
        modifier: Modifier = Modifier,
        autoStart: AutoStart? = null,
    ) {
        setContent {
            var config by remember { mutableStateOf(initial) }
            var returnToGrid by remember { mutableStateOf(ReturnToGrid.OFF) }
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
                        returnToGrid = returnToGrid,
                        onReturnToGridChange = { returnToGrid = it },
                        autoStart = autoStart,
                    )
                }
            }
        }
    }

    /** Tall enough that the lazy list composes every section of a short config at once. */
    private val tall = Modifier.size(width = 1024.dp, height = 4000.dp)

    /** A test window as big as [tall]: content is never larger than its window. */
    private fun runTallTest(block: suspend ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = 1024, height = 4000) { block() }

    private fun ComposeUiTest.top(text: String): Float =
        onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    private val bootSwitch = "Start CamGrid when this device turns on"

    private val returnSection = "Back to grid after inactivity"

    /** The return-to-grid choice labelled [label], scrolled into view first. */
    private fun ComposeUiTest.returnChoice(label: String): SemanticsNodeInteraction {
        onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText(label))
        return onNodeWithText(label)
    }

    /** The boot switch row, scrolled into view first. */
    private fun ComposeUiTest.bootRow(): SemanticsNodeInteraction {
        onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText(bootSwitch))
        return onNodeWithText(bootSwitch)
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
                                returnToGrid = ReturnToGrid.OFF,
                                onReturnToGridChange = {},
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

    @Test
    fun sectionsComeInOrderCamerasFirstAboutLast() = runTallTest {
        showSettings(testConfig(1), tall, FakeAutoStart())
        val tops = listOf("Cameras", "Views", returnSection, "Start on boot", "Backup", "About").map { top(it) }
        assertEquals(tops.sorted(), tops, "section tops $tops")
    }

    @Test
    fun returnToGridOffersOffAndFourTimesWithOffSelectedByDefault() = runComposeUiTest {
        showSettings(testConfig(1))
        returnChoice("Off").assertIsSelected()
        for (label in listOf("1 min", "2 min", "5 min", "10 min")) returnChoice(label).assertIsNotSelected()
        onNodeWithText("a camera opened fullscreen goes back to the grid", substring = true).assertExists()
    }

    @Test
    fun returnToGridChangesWithOkOnTheRemote() = runComposeUiTest {
        showSettings(testConfig(1))
        returnChoice("2 min").requestFocus()
        returnChoice("2 min").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        returnChoice("2 min").assertIsSelected()
        returnChoice("Off").assertIsNotSelected()
        returnChoice("Off").performClick()
        waitForIdle()
        returnChoice("Off").assertIsSelected()
        returnChoice("2 min").assertIsNotSelected()
    }

    @Test
    fun withoutAutoStartTheBootSectionIsHidden() = runTallTest {
        showSettings(testConfig(1), tall, autoStart = null)
        onNodeWithText("About").assertIsDisplayed()
        onNodeWithText("Start on boot").assertDoesNotExist()
        onNodeWithText(bootSwitch).assertDoesNotExist()
    }

    @Test
    fun theBootSwitchShowsAndChangesTheSetting() = runComposeUiTest {
        val autoStart = FakeAutoStart(enabled = false)
        showSettings(testConfig(1), autoStart = autoStart)
        bootRow().assertIsOff()
        onNodeWithText("Starts after a restart or power cut. Waking the TV from standby returns to whatever app was open.")
            .assertExists()
        // OK on the remote toggles the focused row.
        bootRow().requestFocus()
        bootRow().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf(true), autoStart.setCalls)
        bootRow().assertIsOn()
        bootRow().performClick()
        waitForIdle()
        assertEquals(listOf(true, false), autoStart.setCalls)
        bootRow().assertIsOff()
    }

    @Test
    fun theOverlayNoteShowsOnlyWhileEnabledAndBlocked() = runComposeUiTest {
        val autoStart = FakeAutoStart(enabled = false, blockedWhenEnabled = AutoStartBlocker.OVERLAY_PERMISSION)
        showSettings(testConfig(1), autoStart = autoStart)
        bootRow()
        onNodeWithText("Open permission settings").assertDoesNotExist()
        bootRow().performClick()
        waitForIdle()
        onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText("Open permission settings"))
        onNodeWithText("Display over other apps", substring = true).assertExists()
        onNodeWithText("Open permission settings").performClick()
        waitForIdle()
        assertEquals(1, autoStart.settingsOpened)
        // The system screen opened: no need for the manual way.
        onNodeWithText("adb shell", substring = true).assertDoesNotExist()
        bootRow().performClick()
        waitForIdle()
        onNodeWithText("Open permission settings").assertDoesNotExist()
    }

    @Test
    fun theNoteGoesAwayWhenThePermissionWasGrantedOnTheSystemScreen() = runComposeUiTest {
        val autoStart = FakeAutoStart(enabled = true, blockedWhenEnabled = AutoStartBlocker.OVERLAY_PERMISSION)
        val lifecycle = TestLifecycleOwner()
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                CamGridTheme {
                    SettingsScreen(
                        config = testConfig(1),
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
                        onOpenLicenses = {},
                        returnToGrid = ReturnToGrid.OFF,
                        onReturnToGridChange = {},
                        autoStart = autoStart,
                    )
                }
            }
        }
        onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText("Open permission settings"))
        onNodeWithText("Open permission settings").performClick()
        // The system screen covers the app, the user grants the permission and comes back.
        runOnIdle { lifecycle.registry.currentState = Lifecycle.State.STARTED }
        autoStart.blockedWhenEnabled = null
        runOnIdle { lifecycle.registry.currentState = Lifecycle.State.RESUMED }
        waitForIdle()
        onNodeWithText("Open permission settings").assertDoesNotExist()
    }

    /** A lifecycle the test moves by hand; starts resumed like a visible screen. */
    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun enabledButNotBlockedShowsNoNote() = runComposeUiTest {
        showSettings(testConfig(1), autoStart = FakeAutoStart(enabled = true, blockedWhenEnabled = null))
        bootRow().assertIsOn()
        onNodeWithText("Open permission settings").assertDoesNotExist()
    }

    @Test
    fun withoutASystemScreenTheAdbCommandIsShown() = runComposeUiTest {
        val autoStart = FakeAutoStart(
            enabled = true,
            blockedWhenEnabled = AutoStartBlocker.OVERLAY_PERMISSION,
            hasBlockerSettings = false,
        )
        showSettings(testConfig(1), autoStart = autoStart)
        bootRow()
        onNodeWithText("adb shell", substring = true).assertDoesNotExist()
        onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasText("Open permission settings"))
        onNodeWithText("Open permission settings").performClick()
        waitForIdle()
        assertEquals(1, autoStart.settingsOpened)
        onNodeWithTag(SETTINGS_LIST_TAG)
            .performScrollToNode(hasText("adb shell appops set io.github.vandosketch.camgrid SYSTEM_ALERT_WINDOW allow"))
        onNodeWithText("adb shell appops set io.github.vandosketch.camgrid SYSTEM_ALERT_WINDOW allow").assertIsDisplayed()
    }

    @Test
    fun theEmptyCameraListExplainsTheImportNextToItsButtons() = runTallTest {
        showSettings(CamGridConfig(views = threeViews), tall)
        onNodeWithText("No cameras configured.").assertIsDisplayed()
        onNodeWithText("your go2rtc address", substring = true).assertIsDisplayed()
        val emptyTop = top("No cameras configured.")
        // The buttons sit right there, before the Views section.
        assertTrue(top("Add camera") > emptyTop && top("Add camera") < top("Views"))
        assertTrue(top("Import from go2rtc") < top("Views"))
    }
}
