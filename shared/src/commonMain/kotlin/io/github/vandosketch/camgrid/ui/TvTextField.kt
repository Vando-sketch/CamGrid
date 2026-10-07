package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation

/**
 * True on TV devices (Fire TV, Android TV), where the remote's D-pad is the only input. Text
 * fields then need OK to start typing, see [CamTextField]. Provided by the platform app.
 */
val LocalDpadFirst = staticCompositionLocalOf { false }

/**
 * The app's text field. Everywhere but on a TV it is a plain [OutlinedTextField]. With
 * [LocalDpadFirst] D-pad focus only highlights the field: OK starts typing (and opens the
 * keyboard), Back, Done or Up/Down end it, so the D-pad can always move on to the next field.
 *
 * TODO(#14): TV behaviour; this is the API the screens use.
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
}
