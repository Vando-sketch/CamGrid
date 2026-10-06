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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vandosketch.camgrid.CamGridViewModel
import io.github.vandosketch.camgrid.Screen
import io.github.vandosketch.camgrid.platform.VideoPlatform

/**
 * Root composable: shows the current [Screen] and routes Back.
 *
 * Only one screen is in the composition at a time. That is what releases the grid's players
 * before fullscreen starts its own: Compose disposes the leaving grid tiles (releasing their
 * players) before the entering fullscreen's effects run.
 *
 * @param video the platform's players; the screens use nothing else to show streams.
 * @param onImmersiveChange hides the system bars for the grid and fullscreen, shows them otherwise.
 */
@Composable
fun CamGridApp(viewModel: CamGridViewModel, video: VideoPlatform, onImmersiveChange: (Boolean) -> Unit) {
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

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
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
                )
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
                    onExport = viewModel::exportBackup,
                    onExportToFolder = viewModel::exportBackupToFolder,
                    onImport = viewModel::importBackup,
                    onImportFromFolder = viewModel::importBackupFromFolder,
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
        }
    }
}
