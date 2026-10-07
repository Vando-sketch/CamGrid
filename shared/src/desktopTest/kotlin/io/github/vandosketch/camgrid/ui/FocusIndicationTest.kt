package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Whether any pixel has the focus ring's colour ([FocusColor]). */
internal fun ImageBitmap.containsFocusColor(): Boolean {
    val pixels = toPixelMap()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val c = pixels[x, y]
            if (c.red > 0.9f && c.green in 0.65f..0.85f && c.blue < 0.2f) return true
        }
    }
    return false
}

/** Whether a focus ring is visible anywhere on screen. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.focusRingVisible(): Boolean {
    waitForIdle()
    return onRoot().captureToImage().containsFocusColor()
}

/** [KeyboardNavigation] (focus rings with a keyboard and mouse) and [focusBorder]. */
@OptIn(ExperimentalTestApi::class)
class FocusIndicationTest {

    @Test
    fun startsOffForMouseUsers() {
        assertFalse(KeyboardNavigation().active)
    }

    @Test
    fun navigationKeysTurnItOn() {
        for (key in listOf(Key.DirectionRight, Key.Tab, Key.Enter, Key.PageDown)) {
            val navigation = KeyboardNavigation()
            navigation.onKeyDown(key)
            assertTrue(navigation.active, "$key")
        }
    }

    @Test
    fun otherKeysDoNot() {
        val navigation = KeyboardNavigation()
        for (key in listOf(Key.A, Key.Escape, Key.F11, Key.F1, Key.M)) navigation.onKeyDown(key)
        assertFalse(navigation.active)
    }

    @Test
    fun aPressOrTheWheelTurnsItOff() {
        for (type in listOf(PointerEventType.Press, PointerEventType.Scroll)) {
            val navigation = KeyboardNavigation(active = true)
            navigation.onPointerEvent(type, Offset(10f, 10f))
            assertFalse(navigation.active, "$type")
        }
    }

    @Test
    fun onlyARealMoveTurnsItOff() {
        val navigation = KeyboardNavigation(active = true)
        // The first event only tells where the mouse is.
        navigation.onPointerEvent(PointerEventType.Move, Offset(10f, 10f))
        assertTrue(navigation.active)
        // Compose's synthetic moves after a layout change stay where the mouse rests.
        navigation.onPointerEvent(PointerEventType.Enter, Offset(10f, 10f))
        navigation.onPointerEvent(PointerEventType.Move, Offset(10f, 10f))
        assertTrue(navigation.active)
        navigation.onPointerEvent(PointerEventType.Move, Offset(12f, 10f))
        assertFalse(navigation.active)
    }

    private fun ComposeUiTest.showFocusedBox(navigation: KeyboardNavigation?) {
        val requester = FocusRequester()
        setContent {
            CompositionLocalProvider(LocalKeyboardNavigation provides navigation) {
                Box(
                    Modifier
                        .size(100.dp)
                        .background(Color.Black)
                        .focusBorder()
                        .focusRequester(requester)
                        .focusable(),
                )
            }
        }
        runOnIdle { requester.requestFocus() }
    }

    @Test
    fun focusBorderShowsOnlyWhileUsingTheKeys() = runComposeUiTest {
        val navigation = KeyboardNavigation()
        showFocusedBox(navigation)
        // Focused (as after a mouse click on a button), but the mouse is in use.
        assertFalse(focusRingVisible())
        runOnIdle { navigation.onKeyDown(Key.Tab) }
        assertTrue(focusRingVisible())
        runOnIdle { navigation.onPointerEvent(PointerEventType.Press, Offset(1f, 1f)) }
        assertFalse(focusRingVisible())
    }

    @Test
    fun focusBorderAlwaysShowsWithoutAKeyboardAndMouse() = runComposeUiTest {
        // TV and phone: no KeyboardNavigation, focus alone decides, as before.
        showFocusedBox(navigation = null)
        assertTrue(focusRingVisible())
    }
}
