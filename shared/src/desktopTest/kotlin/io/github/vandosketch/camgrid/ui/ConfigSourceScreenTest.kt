package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import io.github.vandosketch.camgrid.SourceSetupState
import io.github.vandosketch.camgrid.core.ConfigSource
import io.github.vandosketch.camgrid.core.RemoteConfigException
import kotlin.test.Test
import kotlin.test.assertEquals

/** The config URL screen: the checks of the URL and token, the confirmation, and the results. */
@OptIn(ExperimentalTestApi::class)
class ConfigSourceScreenTest {

    private val calls = mutableListOf<String>()

    private fun ComposeUiTest.show(current: ConfigSource? = null, setup: SourceSetupState = SourceSetupState.Idle) {
        setContent {
            CamGridTheme {
                ConfigSourceScreen(
                    current = current,
                    setup = setup,
                    onConnect = { url, token -> calls += "connect:$url|$token" },
                    onRemove = { calls += "remove" },
                    onBack = { calls += "back" },
                )
            }
        }
    }

    private fun ComposeUiTest.typeUrl(url: String) {
        onNodeWithTag(SOURCE_URL_TAG).performTextInput(url)
        waitForIdle()
    }

    private fun ComposeUiTest.load() = onNodeWithText("Load")

    @Test
    fun anEmptyUrlCannotBeLoaded() = runComposeUiTest {
        show()
        load().assertIsNotEnabled()
        onNodeWithText("Enter an http:// or https:// address").assertDoesNotExist()
    }

    @Test
    fun httpsLoadsWithoutAWarning() = runComposeUiTest {
        show()
        typeUrl("https://example.com/camgrid.json")
        load().assertIsEnabled()
        onNodeWithText("Plain HTTP", substring = true).assertDoesNotExist()
    }

    @Test
    fun localHttpWarnsButCanBeLoaded() = runComposeUiTest {
        show()
        typeUrl("http://nas.local/camgrid.json")
        onNodeWithText("Plain HTTP: anyone on your network can read this file and the token. Use HTTPS if you can.").assertIsDisplayed()
        load().assertIsEnabled()
    }

    @Test
    fun publicHttpIsRefused() = runComposeUiTest {
        show()
        typeUrl("http://192.0.2.10/camgrid.json")
        onNodeWithText("Plain HTTP only works for addresses on your local network. Use https://.").assertIsDisplayed()
        load().assertIsNotEnabled()
    }

    @Test
    fun credentialsInTheUrlAreRefused() = runComposeUiTest {
        show()
        typeUrl("https://user:secret@example.com/camgrid.json")
        onNodeWithText("No user name or password in the URL. Use the token instead.").assertIsDisplayed()
        load().assertIsNotEnabled()
    }

    @Test
    fun somethingElseIsNotAnAddress() = runComposeUiTest {
        show()
        typeUrl("camgrid.json")
        onNodeWithText("Enter an http:// or https:// address").assertIsDisplayed()
        load().assertIsNotEnabled()
    }

    @Test
    fun aTokenWithASpaceIsRefused() = runComposeUiTest {
        show()
        typeUrl("https://example.com/camgrid.json")
        onNodeWithTag(SOURCE_TOKEN_TAG).performTextInput("two words")
        waitForIdle()
        onNodeWithText("The token can’t contain spaces.").assertIsDisplayed()
        load().assertIsNotEnabled()
    }

    @Test
    fun loadingTheFirstTimeAsksBeforeReplacingTheCameras() = runComposeUiTest {
        show()
        typeUrl("https://example.com/camgrid.json")
        onNodeWithTag(SOURCE_TOKEN_TAG).performTextInput("abc123")
        load().performClick()
        waitForIdle()
        onNodeWithText("Replace the cameras and views on this device with the ones from the URL?").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsFocused().performClick()
        waitForIdle()
        assertEquals(emptyList<String>(), calls)

        load().performClick()
        waitForIdle()
        onNodeWithText("Replace").performClick()
        waitForIdle()
        assertEquals(listOf("connect:https://example.com/camgrid.json|abc123"), calls)
    }

    @Test
    fun aManagedDeviceIsPrefilledAndLoadsWithoutAsking() = runComposeUiTest {
        show(current = ConfigSource("https://example.com/camgrid.json", "abc123"))
        load().performClick()
        waitForIdle()
        onNodeWithText("Replace the cameras", substring = true).assertDoesNotExist()
        assertEquals(listOf("connect:https://example.com/camgrid.json|abc123"), calls)
    }

    @Test
    fun stopUsingTheUrlIsOfferedOnlyWhileOneIsSet() = runComposeUiTest {
        var current by mutableStateOf<ConfigSource?>(ConfigSource("https://example.com/camgrid.json"))
        setContent {
            CamGridTheme {
                ConfigSourceScreen(
                    current = current,
                    setup = SourceSetupState.Idle,
                    onConnect = { _, _ -> },
                    onRemove = { calls += "remove" },
                    onBack = {},
                )
            }
        }
        onNodeWithText("The cameras and views stay", substring = true).assertExists()
        onNodeWithText("Stop using the URL").performClick()
        waitForIdle()
        assertEquals(listOf("remove"), calls)
        runOnIdle { current = null }
        onNodeWithText("Stop using the URL").assertDoesNotExist()
    }

    @Test
    fun connectingShowsProgress() = runComposeUiTest {
        show(setup = SourceSetupState.Connecting)
        onNodeWithText("Loading the config URL…").assertIsDisplayed()
    }

    @Test
    fun aFailedLoadSaysWhy() = runComposeUiTest {
        show(setup = SourceSetupState.Failed(RemoteConfigException.Reason.HTTP_STATUS, "HTTP 401"))
        onNodeWithText("The server answered with an error (HTTP 401).").assertIsDisplayed()
    }

    @Test
    fun anEncryptedBackupIsExplained() = runComposeUiTest {
        show(setup = SourceSetupState.Failed(RemoteConfigException.Reason.ENCRYPTED, ""))
        onNodeWithText("That’s an encrypted backup. Export one without a password.").assertIsDisplayed()
    }

    @Test
    fun backGoesBack() = runComposeUiTest {
        show()
        onNodeWithText("Back").performClick()
        waitForIdle()
        assertEquals(listOf("back"), calls)
    }
}
