package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vandosketch.camgrid.CamGridViewModel
import io.github.vandosketch.camgrid.Screen
import io.github.vandosketch.camgrid.platform.VideoPlatform

/**
 * Root composable: shows the current [Screen] and routes Back: the platform's Back (key, gesture)
 * and, on every screen but the grid, Esc and Backspace (when no text field took it) and the
 * mouse's back button where there is a mouse ([LocalHasKeyboardAndMouse]). Those go through
 * the platform's Back as well, so an inner [BackHandler] (the view editor's) still comes first.
 * On the grid and in fullscreen, ? or F1 shows the [ShortcutsDialog].
 *
 * Only one screen is in the composition at a time. That is what releases the grid's players
 * before fullscreen starts its own: Compose disposes the leaving grid tiles (releasing their
 * players) before the entering fullscreen's effects run.
 *
 * @param video the platform's players; the screens use nothing else to show streams.
 * @param appVersion the version shown in Settings, About; see [io.github.vandosketch.camgrid.about.AppVersion].
 * @param onImmersiveChange hides the system bars for the grid and fullscreen, shows them otherwise.
 */
@Composable
fun CamGridApp(
    viewModel: CamGridViewModel,
    video: VideoPlatform,
    appVersion: String,
    onImmersiveChange: (Boolean) -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val screen = viewModel.screen

    val immersive = screen is Screen.Grid || screen is Screen.Fullscreen
    // Re-applied on every start: the system may show the bars again while the app is stopped.
    LifecycleStartEffect(immersive) {
        onImmersiveChange(immersive)
        onStopOrDispose { }
    }

    // The grid is the home screen: Back there leaves the app (default behaviour).
    BackHandler(enabled = screen !is Screen.Grid) { viewModel.back() }

    val dispatchBack = rememberBackDispatcher()
    var showShortcuts by remember { mutableStateOf(false) }
    val currentScreen by rememberUpdatedState(screen)
    // Desktop only: on Android the system turns the mouse's back button into a Back key itself.
    val mouseBack = if (LocalHasKeyboardAndMouse.current) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && event.buttons.isBackPressed && currentScreen !is Screen.Grid) {
                        event.changes.forEach { it.consume() }
                        dispatchBack()
                    }
                }
            }
        }
    } else {
        Modifier
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // Around the screens: only keys the focused screen did not handle arrive here.
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when {
                        // Not on the grid: Back there would leave the app on Android.
                        event.key.isBackShortcut() && screen !is Screen.Grid -> {
                            dispatchBack()
                            true
                        }
                        event.isHelpShortcut() && immersive -> {
                            showShortcuts = true
                            true
                        }
                        else -> false
                    }
                }
                .then(mouseBack),
        ) {
            when (screen) {
                Screen.Grid -> GridScreen(
                    video = video,
                    config = config,
                    page = viewModel.gridPage,
                    focusIndex = viewModel.gridFocusIndex,
                    onPositionChange = viewModel::setGridPosition,
                    onOpenCamera = viewModel::openCamera,
                    onOpenSettings = viewModel::openSettings,
                )
                is Screen.Fullscreen -> FullscreenScreen(
                    video = video,
                    cameras = config.cameras,
                    cameraId = screen.cameraId,
                    onSwitchCamera = viewModel::openCamera,
                    onClose = viewModel::back,
                )
                Screen.Settings -> SettingsScreen(
                    config = config,
                    onDone = viewModel::back,
                    onEditView = viewModel::editView,
                    onAddView = viewModel::addView,
                    onMoveView = viewModel::moveView,
                    onMoveCamera = viewModel::moveCamera,
                    onEditCamera = viewModel::editCamera,
                    onDeleteCamera = viewModel::deleteCamera,
                    onImport = viewModel::openImport,
                    onBackup = viewModel::openBackup,
                    appVersion = appVersion,
                    onOpenLicenses = viewModel::openLicenses,
                )
                Screen.Licenses -> LicensesScreen(onOpenLicense = viewModel::openLicense, onBack = viewModel::back)
                is Screen.LicenseText -> key(screen.license) {
                    LicenseTextScreen(license = screen.license, onBack = viewModel::back)
                }
                is Screen.EditCamera -> key(screen.cameraId) {
                    CameraEditorScreen(
                        camera = screen.cameraId?.let { id -> config.cameras.find { it.id == id } },
                        onSave = viewModel::saveCamera,
                        onCancel = viewModel::back,
                    )
                }
                Screen.Go2rtcImport -> Go2rtcImportScreen(
                    initialBaseUrl = config.go2rtcBaseUrl,
                    existingIds = config.cameras.map { it.id }.toSet(),
                    state = viewModel.importState,
                    streamType = viewModel.importStreamType,
                    onStreamTypeChange = viewModel::selectImportStreamType,
                    onFetch = viewModel::fetchGo2rtc,
                    onToggle = viewModel::toggleImportSelection,
                    onImport = viewModel::importSelected,
                    onBack = viewModel::back,
                )
                Screen.Backup -> BackupScreen(
                    files = viewModel.backupFiles,
                    state = viewModel.backupState,
                    folderPath = viewModel.backupFolderPath,
                    suggestedName = viewModel.suggestedBackupName(),
                    listFolderFiles = viewModel::backupFolderFiles,
                    lanTransfer = viewModel.lanTransfer,
                    lanDownloadName = viewModel.lanDownloadName,
                    onStartLanTransfer = viewModel::startLanTransfer,
                    onStopLanTransfer = viewModel::stopLanTransfer,
                    onExport = viewModel::exportBackup,
                    onExportToFolder = viewModel::exportBackupToFolder,
                    onImport = viewModel::importBackup,
                    onImportFromFolder = viewModel::importBackupFromFolder,
                    onImportFromDownloads = viewModel::importBackupFromDownloads,
                    onSubmitPassword = viewModel::submitBackupPassword,
                    onConfirmImport = viewModel::confirmImport,
                    onReset = viewModel::resetBackup,
                    onBack = viewModel::back,
                )
                is Screen.EditView -> {
                    val view = config.views.find { it.id == screen.viewId }
                    if (view == null) {
                        // Deleted meanwhile; nothing to edit.
                        LaunchedEffect(screen.viewId) { viewModel.back() }
                    } else {
                        key(screen.viewId) {
                            ViewEditorScreen(
                                view = view,
                                cameras = config.cameras,
                                canDelete = config.views.size > 1,
                                onChange = viewModel::updateView,
                                onDelete = { viewModel.deleteView(view.id) },
                                onDone = viewModel::back,
                            )
                        }
                    }
                }
            }
            if (showShortcuts && immersive) ShortcutsDialog(onDismiss = { showShortcuts = false })
        }
    }
}
