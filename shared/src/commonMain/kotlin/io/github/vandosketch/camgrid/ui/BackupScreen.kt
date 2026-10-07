package io.github.vandosketch.camgrid.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.vandosketch.camgrid.BackupError
import io.github.vandosketch.camgrid.BackupState
import io.github.vandosketch.camgrid.LanTransferState
import io.github.vandosketch.camgrid.platform.BackupDocument
import io.github.vandosketch.camgrid.platform.BackupFiles
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.bk_back
import io.github.vandosketch.camgrid.shared.resources.bk_cameras
import io.github.vandosketch.camgrid.shared.resources.bk_cancel
import io.github.vandosketch.camgrid.shared.resources.bk_downloads_ask
import io.github.vandosketch.camgrid.shared.resources.bk_downloads_denied
import io.github.vandosketch.camgrid.shared.resources.bk_error_newer_version
import io.github.vandosketch.camgrid.shared.resources.bk_error_password_empty
import io.github.vandosketch.camgrid.shared.resources.bk_error_password_mismatch
import io.github.vandosketch.camgrid.shared.resources.bk_error_read_failed
import io.github.vandosketch.camgrid.shared.resources.bk_error_unreadable
import io.github.vandosketch.camgrid.shared.resources.bk_error_write_failed
import io.github.vandosketch.camgrid.shared.resources.bk_export_help
import io.github.vandosketch.camgrid.shared.resources.bk_export_with_password
import io.github.vandosketch.camgrid.shared.resources.bk_export_without_password
import io.github.vandosketch.camgrid.shared.resources.bk_folder_empty
import io.github.vandosketch.camgrid.shared.resources.bk_folder_files
import io.github.vandosketch.camgrid.shared.resources.bk_folder_help
import io.github.vandosketch.camgrid.shared.resources.bk_import_from_file
import io.github.vandosketch.camgrid.shared.resources.bk_imported
import io.github.vandosketch.camgrid.shared.resources.bk_lan_title
import io.github.vandosketch.camgrid.shared.resources.bk_password
import io.github.vandosketch.camgrid.shared.resources.bk_password_repeat
import io.github.vandosketch.camgrid.shared.resources.bk_plain_confirm
import io.github.vandosketch.camgrid.shared.resources.bk_plain_message
import io.github.vandosketch.camgrid.shared.resources.bk_plain_title
import io.github.vandosketch.camgrid.shared.resources.bk_replace_confirm
import io.github.vandosketch.camgrid.shared.resources.bk_replace_message
import io.github.vandosketch.camgrid.shared.resources.bk_replace_title
import io.github.vandosketch.camgrid.shared.resources.bk_saved
import io.github.vandosketch.camgrid.shared.resources.bk_saved_plain_reminder
import io.github.vandosketch.camgrid.shared.resources.bk_section_export
import io.github.vandosketch.camgrid.shared.resources.bk_section_import
import io.github.vandosketch.camgrid.shared.resources.bk_title
import io.github.vandosketch.camgrid.shared.resources.bk_tv_import_local
import io.github.vandosketch.camgrid.shared.resources.bk_unlock
import io.github.vandosketch.camgrid.shared.resources.bk_unlock_message
import io.github.vandosketch.camgrid.shared.resources.bk_unlock_title
import io.github.vandosketch.camgrid.shared.resources.bk_views
import io.github.vandosketch.camgrid.shared.resources.bk_working
import io.github.vandosketch.camgrid.shared.resources.bk_wrong_password

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
 * the platform's file picker from [files]; devices without one fall back to the app's own
 * folder at [folderPath] (adb push / pull).
 *
 * On a TV ([BackupFiles.lanServer] set) there is no usable picker. While this screen is visible
 * (started, in lifecycle terms) the TV serves a transfer page on the local network
 * ([onStartLanTransfer] / [onStopLanTransfer], shown from [lanTransfer]): a phone or computer
 * uploads a backup there, which runs through the same password and confirmation dialogs, or
 * downloads the file exported here ([lanDownloadName]). On older Android TVs the public
 * Download folder ([BackupFiles.downloads]) is listed too. The work and its result live in
 * the ViewModel ([state]).
 */
@Composable
fun BackupScreen(
    files: BackupFiles,
    state: BackupState,
    folderPath: String?,
    suggestedName: String,
    listFolderFiles: () -> List<String>,
    lanTransfer: LanTransferState,
    lanDownloadName: String?,
    onStartLanTransfer: () -> Unit,
    onStopLanTransfer: () -> Unit,
    onExport: (target: BackupDocument, password: CharArray?) -> Unit,
    onExportToFolder: (password: CharArray?) -> Unit,
    onImport: (source: BackupDocument) -> Unit,
    onImportFromFolder: (name: String) -> Unit,
    onImportFromDownloads: (name: String) -> Unit,
    onSubmitPassword: (CharArray) -> Unit,
    onConfirmImport: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val isTv = files.lanServer != null
    if (isTv) {
        val start by rememberUpdatedState(onStartLanTransfer)
        val stop by rememberUpdatedState(onStopLanTransfer)
        // Serves only while the screen is visible: stops when leaving it or going to the background.
        LifecycleStartEffect(Unit) {
            start()
            onStopOrDispose { stop() }
        }
    }
    val downloads = files.downloads
    // Plain remember, not rememberSaveable: passwords stay out of the saved state.
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<PasswordProblem?>(null) }
    var confirmPlain by remember { mutableStateOf(false) }
    // Whether the last export was unencrypted, for the reminder under "Saved: ...".
    var lastExportPlain by remember { mutableStateOf(false) }
    // Files in the app folder, listed once the open dialog turned out to be missing.
    var folderFiles by remember { mutableStateOf<List<String>?>(null) }
    // Files in the public Download folder (older Android TVs), once readable and asked for.
    var downloadFiles by remember { mutableStateOf<List<String>?>(null) }
    var downloadsDenied by remember { mutableStateOf(false) }
    val pending = remember { PendingExport() }

    val pickers = files.rememberPickers(
        onSaveChosen = { target ->
            val chosen = pending.password
            pending.password = null
            if (target != null) {
                // The ViewModel wipes the password once the file is written.
                onExport(target, chosen)
            } else {
                chosen?.fill(' ')
            }
        },
        onOpenChosen = { source -> if (source != null) onImport(source) },
    )

    fun startExport(chosen: CharArray?) {
        lastExportPlain = chosen == null
        password = ""
        repeat = ""
        problem = null
        pending.password = chosen
        if (!pickers.launchSave(suggestedName)) {
            // No file picker (Fire TV): save into the app folder instead.
            pending.password = null
            onExportToFolder(chosen)
        }
    }

    val backRequester = remember { FocusRequester() }
    InitialFocus(backRequester)

    val scrollState = rememberScrollState()
    ScrollbarBox(scrollState, Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenHeader(
                title = stringResource(Res.string.bk_title),
                actionLabel = stringResource(Res.string.bk_back),
                onAction = onBack,
                actionRequester = backRequester,
            )

            // Progress and results sit right under the header, where they are seen without scrolling.
            StatusMessage(state = state, lastExportPlain = lastExportPlain)

            if (isTv) {
                SectionTitle(stringResource(Res.string.bk_lan_title))
                LanTransferPanel(state = lanTransfer, downloadName = lanDownloadName)
            }

            SectionTitle(stringResource(Res.string.bk_section_export))
            HelpText(stringResource(Res.string.bk_export_help))
            PasswordField(
                value = password,
                onValueChange = {
                    password = it
                    problem = null
                },
                label = stringResource(Res.string.bk_password),
                error = null,
                imeAction = ImeAction.Next,
            )
            PasswordField(
                value = repeat,
                onValueChange = {
                    repeat = it
                    problem = null
                },
                label = stringResource(Res.string.bk_password_repeat),
                error = when (problem) {
                    PasswordProblem.EMPTY -> stringResource(Res.string.bk_error_password_empty)
                    PasswordProblem.MISMATCH -> stringResource(Res.string.bk_error_password_mismatch)
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
                    Text(stringResource(Res.string.bk_export_with_password))
                }
                OutlinedButton(
                    onClick = { confirmPlain = true },
                    modifier = Modifier.focusBorder(shape = CircleShape),
                ) {
                    Text(stringResource(Res.string.bk_export_without_password))
                }
            }

            SectionTitle(stringResource(Res.string.bk_section_import))
            Button(
                onClick = {
                    if (!pickers.launchOpen()) {
                        // No usable file picker (TV): offer the files in the app folder instead.
                        folderFiles = listFolderFiles()
                        if (downloads != null && !downloads.needsPermission) downloadFiles = downloads.list()
                    }
                },
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(if (isTv) Res.string.bk_tv_import_local else Res.string.bk_import_from_file))
            }
            folderFiles?.let { names ->
                FileList(folder = folderPath.orEmpty(), names = names, onPick = onImportFromFolder)
                if (downloads != null) {
                    val listed = downloadFiles
                    if (listed != null) {
                        FileList(folder = downloads.path, names = listed, onPick = onImportFromDownloads)
                    } else {
                        OutlinedButton(
                            onClick = {
                                pickers.requestDownloadsAccess { granted ->
                                    downloadsDenied = !granted
                                    if (granted) downloadFiles = downloads.list()
                                }
                            },
                            modifier = Modifier.focusBorder(shape = CircleShape),
                        ) {
                            Text(stringResource(Res.string.bk_downloads_ask))
                        }
                        if (downloadsDenied) HelpText(stringResource(Res.string.bk_downloads_denied))
                    }
                }
            }
            if (folderPath != null) HelpText(stringResource(Res.string.bk_folder_help, folderPath))
        }
    }

    if (confirmPlain) {
        val cancelRequester = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { confirmPlain = false },
            title = { Text(stringResource(Res.string.bk_plain_title)) },
            text = { Text(stringResource(Res.string.bk_plain_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPlain = false
                        startExport(null)
                    },
                    modifier = Modifier.focusBorder(shape = CircleShape),
                ) {
                    Text(stringResource(Res.string.bk_plain_confirm))
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
                    Text(stringResource(Res.string.bk_cancel))
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

/** The backup files [names] in [folder], one button each; a note when there are none. */
@Composable
private fun FileList(folder: String, names: List<String>, onPick: (String) -> Unit) {
    if (names.isEmpty()) {
        HelpText(stringResource(Res.string.bk_folder_empty, folder))
        return
    }
    HelpText(stringResource(Res.string.bk_folder_files, folder))
    names.forEach { name ->
        OutlinedButton(
            onClick = { onPick(name) },
            modifier = Modifier.focusBorder(shape = CircleShape),
        ) {
            Text(name)
        }
    }
}

@Composable
private fun StatusMessage(state: BackupState, lastExportPlain: Boolean) {
    when (state) {
        BackupState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
            // Deriving the key from the password takes about a second.
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(Res.string.bk_working))
        }
        is BackupState.Exported -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.bk_saved, state.where),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (lastExportPlain) HelpText(stringResource(Res.string.bk_saved_plain_reminder))
        }
        BackupState.Imported -> Text(
            text = stringResource(Res.string.bk_imported),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        is BackupState.Failed -> Text(
            text = stringResource(
                when (state.error) {
                    BackupError.UNREADABLE -> Res.string.bk_error_unreadable
                    BackupError.NEWER_VERSION -> Res.string.bk_error_newer_version
                    BackupError.READ_FAILED -> Res.string.bk_error_read_failed
                    BackupError.WRITE_FAILED -> Res.string.bk_error_write_failed
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
        title = { Text(stringResource(Res.string.bk_unlock_title)) },
        text = {
            // The effect lives inside the dialog's content so the field exists when it runs.
            LaunchedEffect(Unit) { fieldRequester.requestFocusAfterLayout() }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.bk_unlock_message))
                PasswordField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(Res.string.bk_password),
                    error = if (wrongPassword) stringResource(Res.string.bk_wrong_password) else null,
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
                Text(stringResource(Res.string.bk_unlock))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(Res.string.bk_cancel))
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
        title = { Text(stringResource(Res.string.bk_replace_title)) },
        text = {
            Text(
                stringResource(
                    Res.string.bk_replace_message,
                    pluralStringResource(Res.plurals.bk_cameras, state.cameraCount, state.cameraCount),
                    pluralStringResource(Res.plurals.bk_views, state.viewCount, state.viewCount),
                    pluralStringResource(Res.plurals.bk_cameras, state.currentCameraCount, state.currentCameraCount),
                    pluralStringResource(Res.plurals.bk_views, state.currentViewCount, state.currentViewCount),
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(Res.string.bk_replace_confirm))
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
                Text(stringResource(Res.string.bk_cancel))
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
    CamTextField(
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
internal fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
