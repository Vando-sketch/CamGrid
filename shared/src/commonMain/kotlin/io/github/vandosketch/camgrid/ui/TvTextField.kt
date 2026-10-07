package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * True on TV devices (Fire TV, Android TV), where the remote's D-pad is the only input. Text
 * fields then need OK to start typing, see [CamTextField]. Provided by the platform app.
 */
val LocalDpadFirst = staticCompositionLocalOf { false }

/**
 * The app's text field. Everywhere but on a TV it is a plain [OutlinedTextField]. With
 * [LocalDpadFirst] D-pad focus only highlights the field: OK starts typing (and opens the
 * keyboard), Back, Done or Up/Down end it, so the D-pad can always move on to the next field.
 * A tap or click still starts typing straight away.
 *
 * On a TV [modifier] goes on the outer, D-pad focusable box, so a caller's focus requester
 * focuses the field without opening the keyboard.
 */
@Composable
fun CamTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    if (!LocalDpadFirst.current) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label,
            placeholder = placeholder,
            supportingText = supportingText,
            isError = isError,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
        return
    }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val outer = remember { FocusRequester() }
    val inner = remember { FocusRequester() }
    // Editing: the inner text field may take focus, and with it the keyboard.
    var editing by remember { mutableStateOf(false) }
    var innerFocused by remember { mutableStateOf(false) }
    // OK went down on this field. Editing starts on its release, so the release does not
    // reach the text field, and a release left over from another element does not count.
    var okDown by remember { mutableStateOf(false) }
    // Back went down while editing; its release must not reach the app's back handling.
    var backDown by remember { mutableStateOf(false) }

    fun startEditing() {
        editing = true
        // The focus properties read [editing] when asked, so the request can go out now.
        inner.tryRequestFocus()
    }

    fun stopEditing() {
        keyboard?.hide()
        if (innerFocused) outer.tryRequestFocus()
        editing = false
    }

    fun KeyboardActionScope.finish(action: (KeyboardActionScope.() -> Unit)?) {
        action?.invoke(this)
        if (innerFocused) stopEditing()
    }

    fun KeyboardActionScope.move(action: (KeyboardActionScope.() -> Unit)?, direction: FocusDirection) {
        action?.invoke(this)
        // The caller's action may have moved focus itself.
        if (innerFocused && !focusManager.moveFocus(direction)) stopEditing()
    }

    // Fallback for a focus request that went out before the field was ready.
    LaunchedEffect(editing) {
        if (editing && !innerFocused) inner.requestFocusAfterLayout()
    }

    Box(
        modifier = modifier
            .focusBorder(shape = RoundedCornerShape(4.dp))
            .focusRequester(outer)
            .onKeyEvent { event -> !editing && onOuterKey(event, okDown, { okDown = it }, ::startEditing) }
            .pointerInput(Unit) {
                // A tap or click starts editing; a drag (scrolling the list) does not.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        if (!change.pressed) {
                            if (!editing) startEditing()
                            break
                        }
                    }
                }
            }
            .focusable(),
        propagateMinConstraints = true,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .focusRequester(inner)
                .focusProperties { canFocus = editing }
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        innerFocused = true
                        editing = true
                    } else if (innerFocused) {
                        innerFocused = false
                        editing = false
                    }
                }
                .onPreviewKeyEvent { event ->
                    when {
                        !editing -> false
                        event.key == Key.Back || event.key == Key.Escape -> {
                            // On Android the keyboard may take the first Back to hide itself;
                            // the next one gets here.
                            when (event.type) {
                                KeyEventType.KeyDown -> backDown = true
                                KeyEventType.KeyUp -> if (backDown) {
                                    backDown = false
                                    stopEditing()
                                }
                            }
                            true
                        }
                        !singleLine -> false
                        event.key == Key.DirectionUp || event.key == Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown) {
                                val direction = if (event.key == Key.DirectionUp) FocusDirection.Up else FocusDirection.Down
                                keyboard?.hide()
                                if (!focusManager.moveFocus(direction)) stopEditing()
                            }
                            true
                        }
                        else -> false
                    }
                },
            label = label,
            placeholder = placeholder,
            supportingText = supportingText,
            isError = isError,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(
                onDone = { finish(keyboardActions.onDone) },
                onGo = { finish(keyboardActions.onGo) },
                onSearch = { finish(keyboardActions.onSearch) },
                onSend = { finish(keyboardActions.onSend) },
                onNext = { move(keyboardActions.onNext, FocusDirection.Next) },
                onPrevious = { move(keyboardActions.onPrevious, FocusDirection.Previous) },
            ),
        )
    }
}

/** OK on the field while it is not editing: consumes the press, starts editing on release. */
private fun onOuterKey(
    event: KeyEvent,
    okDown: Boolean,
    setOkDown: (Boolean) -> Unit,
    startEditing: () -> Unit,
): Boolean {
    if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) return false
    when (event.type) {
        KeyEventType.KeyDown -> setOkDown(true)
        KeyEventType.KeyUp -> if (okDown) {
            setOkDown(false)
            startEditing()
        }
    }
    return true
}
