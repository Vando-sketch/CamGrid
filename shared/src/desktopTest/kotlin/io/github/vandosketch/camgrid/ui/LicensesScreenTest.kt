package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import io.github.vandosketch.camgrid.about.License
import io.github.vandosketch.camgrid.about.ThirdPartyComponents
import io.github.vandosketch.camgrid.core.CamGridConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/** The About section in Settings, the Licenses list and a license text, by touch and by D-pad. */
@OptIn(ExperimentalTestApi::class)
class LicensesScreenTest {

    private lateinit var focusManager: FocusManager

    private fun ComposeUiTest.show(content: @Composable () -> Unit) {
        setContent {
            focusManager = LocalFocusManager.current
            CamGridTheme { content() }
        }
    }

    // On Android the platform turns an unconsumed D-pad key into a focus move; the desktop test
    // host does not, so D-pad navigation runs the same focus search directly.
    private fun ComposeUiTest.dpad(direction: FocusDirection, times: Int = 1) {
        repeat(times) { runOnIdle { focusManager.moveFocus(direction) } }
    }

    @Test
    fun settingsShowsTheVersionAndOpensTheLicenses() = runComposeUiTest {
        var opened = 0
        show {
            SettingsScreen(
                config = CamGridConfig(),
                onDone = {},
                onEditView = {},
                onAddView = {},
                onMoveView = { _, _ -> },
                onMoveCamera = { _, _ -> },
                onEditCamera = {},
                onDeleteCamera = {},
                onImport = {},
                onBackup = {},
                appVersion = "0.1.0-preview.91",
                onOpenLicenses = { opened++ },
            )
        }
        onNodeWithText("CamGrid 0.1.0-preview.91").performScrollTo().assertIsDisplayed()
        onNodeWithText("github.com/Vando-sketch/CamGrid", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("Open-source licenses").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun aComponentOpensItsLicenseByTouch() = runComposeUiTest {
        var opened: License? = null
        show { LicensesScreen(onOpenLicense = { opened = it }, onBack = {}) }
        val ffmpeg = ThirdPartyComponents.all.first { it.name.startsWith("FFmpeg") }
        onNodeWithTag(LICENSES_LIST_TAG).performScrollToNode(hasTestTag(componentTag(ffmpeg.name)))
        onNodeWithTag(componentTag(ffmpeg.name)).performClick()
        assertEquals(License.LGPL_3_0, opened)
        onNodeWithTag(LICENSES_LIST_TAG).performScrollToNode(hasTestTag(licenseTag(License.GPL_3_0)))
        onNodeWithTag(licenseTag(License.GPL_3_0)).performClick()
        assertEquals(License.GPL_3_0, opened)
    }

    @Test
    fun theDpadReachesEveryComponentAndEveryLicenseText() = runComposeUiTest {
        var back = 0
        show { LicensesScreen(onOpenLicense = {}, onBack = { back++ }) }
        onNodeWithText("Back").requestFocus()
        for (component in ThirdPartyComponents.all) {
            dpad(FocusDirection.Down)
            onNodeWithTag(componentTag(component.name)).assertIsFocused().assertIsDisplayed()
        }
        for (license in License.entries) {
            dpad(FocusDirection.Down)
            onNodeWithTag(licenseTag(license)).assertIsFocused().assertIsDisplayed()
        }
        dpad(FocusDirection.Up, times = License.entries.size + ThirdPartyComponents.all.size)
        onNodeWithText("Back").assertIsFocused().performClick()
        assertEquals(1, back)
    }

    @Test
    fun aLicenseTextScrollsWithTheDpad() = runComposeUiTest {
        show { LicenseTextScreen(license = License.MIT, onBack = {}) }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasText("Permission is hereby granted", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Back").requestFocus()
        // Title, copyright, permission, conditions, warranty: each paragraph takes the focus.
        dpad(FocusDirection.Down, times = 5)
        onNodeWithText("THE SOFTWARE IS PROVIDED \"AS IS\"", substring = true).assertIsFocused().assertIsDisplayed()
    }
}
