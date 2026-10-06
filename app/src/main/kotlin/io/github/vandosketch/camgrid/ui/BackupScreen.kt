package io.github.vandosketch.camgrid.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.BackupError
import io.github.vandosketch.camgrid.BackupState
import io.github.vandosketch.camgrid.R

/** MIME types offered in the open dialog; some file managers label .json files as text or binary. */
private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/** Why "Export with password" did not start; shown under the password fields. */
private enum class PasswordProblem { EMPTY, MISMATCH }

/**
 * Holds the password between launching the save dialog and its result. Deliberately a plain
 * [remember] holder: passwords must not end up in the saved instance state.
 */
private class PendingExport {
    var password: CharArray? = null
}

/**
 * Export of all settings to a (optionally password-encrypted) file and import from one. Uses
 * the system file picker; devices without one (Fire TV) fall back to the app's own folder at
 * [folderPath], filled with adb push. The work and its result live in the ViewModel ([state]).
 */
@Composable
fun BackupScreen(
    state: BackupState,
    folderPath: String,
    suggestedName: String,
    listFolderFiles: () -> List<String>,
    onExport: (uri: Uri, password: CharArray?) -> Unit,
    onExportToFolder: (password: CharArray?) -> Unit,
    onImport: (uri: Uri) -> Unit,
    onImportFromFolder: (name: String) -> Unit,
    onSubmitPassword: (CharArray) -> Unit,
    onConfirmImport: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    // Plain remember, not rememberSaveable: passwords stay out of the saved state.
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<PasswordProblem?>(null) }
    var confirmPlain by remember { mutableStateOf(false) }
    // Whether the last export was unencrypted, for the reminder under "Saved: ...".
    var lastExportPlain by remember { mutableStateOf(false) }
    // Files in the app folder, listed once the open dialog turned out to be missing.
    var folderFiles by remember { mutableStateOf<List<String>?>(null) }
    val pending = remember { PendingExport() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val chosen = pending.password
        pending.password = null
        if (uri != null) {
            // The ViewModel wipes the password once the file is written.
            onExport(uri, chosen)
        } else {
            chosen?.fill(' ')
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    fun startExport(chosen: CharArray?) {
        lastExportPlain = chosen == null
        password = ""
        repeat = ""
        problem = null
        pending.password = chosen
        try {
            exportLauncher.launch(suggestedName)
        } catch (e: ActivityNotFoundException) {
            // No file picker (Fire TV): save into the app folder instead.
            pending.password = null
            onExportToFolder(chosen)
        }
    }

    val backRequester = remember { FocusRequester() }
    InitialFocus(backRequester)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.bk_title),
            actionLabel = stringResource(R.string.bk_back),
            onAction = onBack,
            actionRequester = backRequester,
        )

        // Progress and results sit right under the header, where they are seen without scrolling.
        StatusMessage(state = state, lastExportPlain = lastExportPlain)

        SectionTitle(stringResource(R.string.bk_section_export))
        HelpText(stringResource(R.string.bk_export_help))
        PasswordField(
            value = password,
            onValueChange = {
                password = it
                problem = null
            },
            label = stringResource(R.string.bk_password),
            error = null,
            imeAction = ImeAction.Next,
        )
        PasswordField(
            value = repeat,
            onValueChange = {
                repeat = it
                problem = null
            },
            label = stringResource(R.string.bk_password_repeat),
            error = when (problem) {
                PasswordProblem.EMPTY -> stringResource(R.string.bk_error_password_empty)
                PasswordProblem.MISMATCH -> stringResource(R.string.bk_error_password_mismatch)
                null -> null
            },
            imeAction = ImeAction.Done,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Never disabled: a disabled button would drop D-pad focus. Problems show as text.
            Button(
                onClick = {
                    problem = when {
                        password.isEmpty() -> PasswordProblem.EMPTY
                        password != repeat -> PasswordProblem.MISMATCH
                        else -> null
                    }
                    if (problem == null) startExport(password.toCharArray())
                },
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(R.string.bk_export_with_password))
            }
            OutlinedButton(
                onClick = { confirmPlain = true },
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(R.string.bk_export_without_password))
            }
        }

        SectionTitle(stringResource(R.string.bk_section_import))
        Button(
            onClick = {
                try {
                    importLauncher.launch(IMPORT_MIME_TYPES)
                } catch (e: ActivityNotFoundException) {
                    // No file picker (Fire TV): offer the files in the app folder instead.
                    folderFiles = listFolderFiles()
                }
            },
            modifier = Modifier.focusBorder(shape = CircleShape),
        ) {
            Text(stringResource(R.string.bk_import_from_file))
        }
        folderFiles?.let { files ->
            if (files.isEmpty()) {
                HelpText(stringResource(R.string.bk_folder_empty, folderPath))
            } else {
                HelpText(stringResource(R.string.bk_folder_files, folderPath))
                files.forEach { name ->
                    OutlinedButton(
                        onClick = { onImportFromFolder(name) },
                        modifier = Modifier.focusBorder(shape = CircleShape),
                    ) {
                        Text(name)
                    }
                }
            }
        }
        HelpText(stringResource(R.string.bk_folder_help, folderPath))
    }

    if (confirmPlain) {
        val cancelRequester = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { confirmPlain = false },
            title = { Text(stringResource(R.string.bk_plain_title)) },
            text = { Text(stringResource(R.string.bk_plain_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPlain = false
                        startExport(null)
                    },
                    modifier = Modifier.focusBorder(shape = CircleShape),
                ) {
                    Text(stringResource(R.string.bk_plain_confirm))
                }
            },
            dismissButton = {
                // Focused first, so an accidental OK press does not write an unprotected file.
                LaunchedEffect(Unit) { cancelRequester.requestFocusAfterLayout() }
                TextButton(
                    onClick = { confirmPlain = false },
                    modifier = Modifier
                        .focusBorder(shape = CircleShape)
                        .focusRequester(cancelRequester),
                ) {
                    Text(stringResource(R.string.bk_cancel))
                }
            },
        )
    }

    when (state) {
        is BackupState.NeedsPassword -> UnlockDialog(
            wrongPassword = state.wrongPassword,
            onSubmit = onSubmitPassword,
            onCancel = onReset,
        )
        is BackupState.ConfirmImport -> ReplaceDialog(
            state = state,
            onConfirm = onConfirmImport,
            onCancel = onReset,
        )
        else -> Unit
    }
}

@Composable
private fun StatusMessage(state: BackupState, lastExportPlain: Boolean) {
    when (state) {
        BackupState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
            // Deriving the key from the password takes about a second.
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.bk_working))
        }
        is BackupState.Exported -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.bk_saved, state.where),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (lastExportPlain) HelpText(stringResource(R.string.bk_saved_plain_reminder))
        }
        BackupState.Imported -> Text(
            text = stringResource(R.string.bk_imported),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        is BackupState.Failed -> Text(
            text = stringResource(
                when (state.error) {
                    BackupError.UNREADABLE -> R.string.bk_error_unreadable
                    BackupError.NEWER_VERSION -> R.string.bk_error_newer_version
                    BackupError.READ_FAILED -> R.string.bk_error_read_failed
                    BackupError.WRITE_FAILED -> R.string.bk_error_write_failed
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        // Idle shows nothing; the dialogs for the other states are drawn by BackupScreen.
        else -> Unit
    }
}

@Composable
private fun UnlockDialog(
    wrongPassword: Boolean,
    onSubmit: (CharArray) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val fieldRequester = remember { FocusRequester() }
    fun submit() {
        onSubmit(text.toCharArray())
        text = ""
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.bk_unlock_title)) },
        text = {
            // The effect lives inside the dialog's content so the field exists when it runs.
            LaunchedEffect(Unit) { fieldRequester.requestFocusAfterLayout() }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.bk_unlock_message))
                PasswordField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.bk_password),
                    error = if (wrongPassword) stringResource(R.string.bk_wrong_password) else null,
                    imeAction = ImeAction.Done,
                    onDone = { submit() },
                    modifier = Modifier.focusRequester(fieldRequester),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { submit() },
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(R.string.bk_unlock))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(R.string.bk_cancel))
            }
        },
    )
}

@Composable
private fun ReplaceDialog(
    state: BackupState.ConfirmImport,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cancelRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.bk_replace_title)) },
        text = {
            Text(
                stringResource(
                    R.string.bk_replace_message,
                    pluralStringResource(R.plurals.bk_cameras, state.cameraCount, state.cameraCount),
                    pluralStringResource(R.plurals.bk_views, state.viewCount, state.viewCount),
                    pluralStringResource(R.plurals.bk_cameras, state.currentCameraCount, state.currentCameraCount),
                    pluralStringResource(R.plurals.bk_views, state.currentViewCount, state.currentViewCount),
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(R.string.bk_replace_confirm))
            }
        },
        dismissButton = {
            // Focused first, so an accidental OK press does not replace the settings.
            LaunchedEffect(Unit) { cancelRequester.requestFocusAfterLayout() }
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .focusBorder(shape = CircleShape)
                    .focusRequester(cancelRequester),
            ) {
                Text(stringResource(R.string.bk_cancel))
            }
        },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = {
            // Shown in the error colour automatically when isError is true.
            if (error != null) Text(error)
        },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
