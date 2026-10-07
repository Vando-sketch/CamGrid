package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [CamTextField] on a TV ([LocalDpadFirst]) and everywhere else. Two fields stacked: "first"
 * (with the caller's focus requester) above "second". On a TV the test tag sits on the outer,
 * D-pad focusable box; the editable node is the one with the set-text action.
 */
@OptIn(ExperimentalTestApi::class)
class CamTextFieldTest {
    private val requester = FocusRequester()
    private var first by mutableStateOf("")
    private var second by mutableStateOf("")
    private var dones = 0
    private lateinit var focusManager: FocusManager

    private fun ComposeUiTest.show(dpadFirst: Boolean, firstIme: ImeAction = ImeAction.Next) {
        setContent {
            focusManager = LocalFocusManager.current
            CompositionLocalProvider(LocalDpadFirst provides dpadFirst) {
                CamGridTheme {
                    Column {
                        CamTextField(
                            value = first,
                            onValueChange = { first = it },
                            label = { Text("First") },
                            keyboardOptions = KeyboardOptions(imeAction = firstIme),
                            keyboardActions = KeyboardActions(onDone = { dones++ }),
                            modifier = Modifier.testTag("first").focusRequester(requester),
                        )
                        CamTextField(
                            value = second,
                            onValueChange = { second = it },
                            label = { Text("Second") },
                            modifier = Modifier.testTag("second"),
                        )
                    }
                }
            }
        }
        runOnIdle { requester.requestFocus() }
    }

    private fun ComposeUiTest.editable(label: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasText(label))

    private fun ComposeUiTest.press(key: Key) {
        onNode(isFocused()).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    // On Android the platform turns an unconsumed D-pad key into a focus move. Desktop does not
    // do that for arrow keys, so D-pad navigation between idle fields runs the same focus search.
    private fun ComposeUiTest.dpad(direction: FocusDirection) {
        runOnIdle { focusManager.moveFocus(direction) }
    }

    @Test
    fun tvCallerFocusRequesterLandsOnTheOuterField() = runComposeUiTest {
        show(dpadFirst = true)
        onNodeWithTag("first").assertIsFocused()
        editable("First").assertIsNotFocused()
    }

    @Test
    fun tvDpadMovesBetweenFieldsWithoutEditing() = runComposeUiTest {
        show(dpadFirst = true)
        dpad(FocusDirection.Down)
        onNodeWithTag("second").assertIsFocused()
        editable("First").assertIsNotFocused()
        editable("Second").assertIsNotFocused()
        dpad(FocusDirection.Up)
        onNodeWithTag("first").assertIsFocused()
        editable("First").assertIsNotFocused()
    }

    @Test
    fun tvOkStartsEditing() = runComposeUiTest {
        show(dpadFirst = true)
        press(Key.DirectionCenter)
        editable("First").assertIsFocused()
        editable("First").performTextInput("garden")
        waitForIdle()
        assertEquals("garden", first)
    }

    @Test
    fun tvEnterStartsEditing() = runComposeUiTest {
        show(dpadFirst = true)
        press(Key.Enter)
        editable("First").assertIsFocused()
    }

    @Test
    fun tvDownWhileEditingMovesToTheNextField() = runComposeUiTest {
        show(dpadFirst = true)
        press(Key.Enter)
        editable("First").assertIsFocused()
        press(Key.DirectionDown)
        onNodeWithTag("second").assertIsFocused()
        editable("First").assertIsNotFocused()
        editable("Second").assertIsNotFocused()
        // Back up: the first field is no longer editing either.
        dpad(FocusDirection.Up)
        onNodeWithTag("first").assertIsFocused()
        editable("First").assertIsNotFocused()
    }

    @Test
    fun tvUpWhileEditingMovesToThePreviousField() = runComposeUiTest {
        show(dpadFirst = true)
        dpad(FocusDirection.Down)
        press(Key.Enter)
        editable("Second").assertIsFocused()
        press(Key.DirectionUp)
        onNodeWithTag("first").assertIsFocused()
        editable("First").assertIsNotFocused()
        editable("Second").assertIsNotFocused()
    }

    @Test
    fun tvEscapeEndsEditing() = runComposeUiTest {
        show(dpadFirst = true)
        press(Key.Enter)
        editable("First").assertIsFocused()
        press(Key.Escape)
        onNodeWithTag("first").assertIsFocused()
        editable("First").assertIsNotFocused()
        // And the D-pad moves on again.
        dpad(FocusDirection.Down)
        onNodeWithTag("second").assertIsFocused()
    }

    @Test
    fun tvBackEndsEditing() = runComposeUiTest {
        show(dpadFirst = true)
        press(Key.Enter)
        press(Key.Back)
        onNodeWithTag("first").assertIsFocused()
        editable("First").assertIsNotFocused()
    }

    @Test
    fun tvImeNextMovesToTheNextField() = runComposeUiTest {
        show(dpadFirst = true)
        press(Key.Enter)
        editable("First").performImeAction()
        waitForIdle()
        onNodeWithTag("second").assertIsFocused()
        editable("Second").assertIsNotFocused()
    }

    @Test
    fun tvImeDoneEndsEditingAndRunsTheCallersAction() = runComposeUiTest {
        show(dpadFirst = true, firstIme = ImeAction.Done)
        press(Key.Enter)
        editable("First").performImeAction()
        waitForIdle()
        assertEquals(1, dones)
        onNodeWithTag("first").assertIsFocused()
        editable("First").assertIsNotFocused()
    }

    @Test
    fun tvTapStartsEditing() = runComposeUiTest {
        show(dpadFirst = true)
        editable("Second").performClick()
        waitForIdle()
        editable("Second").assertIsFocused()
        editable("Second").performTextInput("porch")
        waitForIdle()
        assertEquals("porch", second)
    }

    @Test
    fun plainCallerFocusRequesterFocusesTheEditable() = runComposeUiTest {
        show(dpadFirst = false)
        editable("First").assertIsFocused()
        editable("First").performTextInput("garden")
        waitForIdle()
        assertEquals("garden", first)
    }

    @Test
    fun plainTapFocusesTheEditable() = runComposeUiTest {
        show(dpadFirst = false)
        editable("Second").performClick()
        waitForIdle()
        editable("Second").assertIsFocused()
    }
}
