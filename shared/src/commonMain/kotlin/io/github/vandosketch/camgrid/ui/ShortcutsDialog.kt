package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.close
import io.github.vandosketch.camgrid.shared.resources.keys_arrows
import io.github.vandosketch.camgrid.shared.resources.keys_back
import io.github.vandosketch.camgrid.shared.resources.keys_back_action
import io.github.vandosketch.camgrid.shared.resources.keys_digits
import io.github.vandosketch.camgrid.shared.resources.keys_enter
import io.github.vandosketch.camgrid.shared.resources.keys_fullscreen_digits
import io.github.vandosketch.camgrid.shared.resources.keys_fullscreen_left_right
import io.github.vandosketch.camgrid.shared.resources.keys_fullscreen_sound
import io.github.vandosketch.camgrid.shared.resources.keys_grid_arrows
import io.github.vandosketch.camgrid.shared.resources.keys_grid_digits
import io.github.vandosketch.camgrid.shared.resources.keys_grid_enter
import io.github.vandosketch.camgrid.shared.resources.keys_grid_page
import io.github.vandosketch.camgrid.shared.resources.keys_help
import io.github.vandosketch.camgrid.shared.resources.keys_help_action
import io.github.vandosketch.camgrid.shared.resources.keys_left_right
import io.github.vandosketch.camgrid.shared.resources.keys_page
import io.github.vandosketch.camgrid.shared.resources.keys_section_fullscreen
import io.github.vandosketch.camgrid.shared.resources.keys_section_general
import io.github.vandosketch.camgrid.shared.resources.keys_section_grid
import io.github.vandosketch.camgrid.shared.resources.keys_sound
import io.github.vandosketch.camgrid.shared.resources.keys_title
import io.github.vandosketch.camgrid.shared.resources.keys_window
import io.github.vandosketch.camgrid.shared.resources.keys_window_action
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The keyboard shortcuts of the camera wall and fullscreen, opened with ? or F1 there. The
 * window fullscreen keys only exist in the desktop app, so they are listed only there.
 */
@Composable
fun ShortcutsDialog(onDismiss: () -> Unit) {
    val closeRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.keys_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Section(Res.string.keys_section_grid)
                Shortcut(Res.string.keys_arrows, Res.string.keys_grid_arrows)
                Shortcut(Res.string.keys_enter, Res.string.keys_grid_enter)
                Shortcut(Res.string.keys_digits, Res.string.keys_grid_digits)
                Shortcut(Res.string.keys_page, Res.string.keys_grid_page)
                Section(Res.string.keys_section_fullscreen)
                Shortcut(Res.string.keys_left_right, Res.string.keys_fullscreen_left_right)
                Shortcut(Res.string.keys_digits, Res.string.keys_fullscreen_digits)
                Shortcut(Res.string.keys_sound, Res.string.keys_fullscreen_sound)
                Section(Res.string.keys_section_general)
                Shortcut(Res.string.keys_back, Res.string.keys_back_action)
                if (LocalHasKeyboardAndMouse.current) Shortcut(Res.string.keys_window, Res.string.keys_window_action)
                Shortcut(Res.string.keys_help, Res.string.keys_help_action)
            }
        },
        confirmButton = {
            // The effect lives inside the dialog's content so the button exists when it runs.
            LaunchedEffect(Unit) { closeRequester.requestFocusAfterLayout() }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .focusBorder(shape = CircleShape)
                    .focusRequester(closeRequester),
            ) {
                Text(stringResource(Res.string.close))
            }
        },
    )
}

@Composable
private fun Section(title: StringResource) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Shortcut(keys: StringResource, action: StringResource) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(
            text = stringResource(keys),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(160.dp),
        )
        Text(
            text = stringResource(action),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
