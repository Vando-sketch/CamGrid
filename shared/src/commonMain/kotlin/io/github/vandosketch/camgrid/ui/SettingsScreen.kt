package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.about.PROJECT_URL
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.about_license
import io.github.vandosketch.camgrid.shared.resources.about_project_page
import io.github.vandosketch.camgrid.shared.resources.about_version
import io.github.vandosketch.camgrid.shared.resources.add_camera
import io.github.vandosketch.camgrid.shared.resources.add_view
import io.github.vandosketch.camgrid.shared.resources.autostart_help
import io.github.vandosketch.camgrid.shared.resources.autostart_manual
import io.github.vandosketch.camgrid.shared.resources.autostart_open_permission
import io.github.vandosketch.camgrid.shared.resources.autostart_overlay_needed
import io.github.vandosketch.camgrid.shared.resources.autostart_switch
import io.github.vandosketch.camgrid.shared.resources.cancel
import io.github.vandosketch.camgrid.shared.resources.delete
import io.github.vandosketch.camgrid.shared.resources.delete_confirm
import io.github.vandosketch.camgrid.shared.resources.delete_message
import io.github.vandosketch.camgrid.shared.resources.delete_title
import io.github.vandosketch.camgrid.shared.resources.done
import io.github.vandosketch.camgrid.shared.resources.edit
import io.github.vandosketch.camgrid.shared.resources.import_go2rtc
import io.github.vandosketch.camgrid.shared.resources.move_down
import io.github.vandosketch.camgrid.shared.resources.move_up
import io.github.vandosketch.camgrid.shared.resources.no_cameras
import io.github.vandosketch.camgrid.shared.resources.no_cameras_help
import io.github.vandosketch.camgrid.shared.resources.open_backup
import io.github.vandosketch.camgrid.shared.resources.open_licenses
import io.github.vandosketch.camgrid.shared.resources.return_to_grid_help
import io.github.vandosketch.camgrid.shared.resources.return_to_grid_minutes
import io.github.vandosketch.camgrid.shared.resources.return_to_grid_off
import io.github.vandosketch.camgrid.shared.resources.section_about
import io.github.vandosketch.camgrid.shared.resources.section_autostart
import io.github.vandosketch.camgrid.shared.resources.section_backup
import io.github.vandosketch.camgrid.shared.resources.section_cameras
import io.github.vandosketch.camgrid.shared.resources.section_config_url
import io.github.vandosketch.camgrid.shared.resources.source_check_now
import io.github.vandosketch.camgrid.shared.resources.source_change
import io.github.vandosketch.camgrid.shared.resources.source_checking
import io.github.vandosketch.camgrid.shared.resources.source_failed
import io.github.vandosketch.camgrid.shared.resources.source_managed_note
import io.github.vandosketch.camgrid.shared.resources.source_off
import io.github.vandosketch.camgrid.shared.resources.source_reason_credentials
import io.github.vandosketch.camgrid.shared.resources.source_reason_encrypted
import io.github.vandosketch.camgrid.shared.resources.source_reason_invalid_url
import io.github.vandosketch.camgrid.shared.resources.source_reason_network
import io.github.vandosketch.camgrid.shared.resources.source_reason_newer_version
import io.github.vandosketch.camgrid.shared.resources.source_reason_too_large
import io.github.vandosketch.camgrid.shared.resources.source_reason_unreadable
import io.github.vandosketch.camgrid.shared.resources.source_set_up
import io.github.vandosketch.camgrid.shared.resources.source_up_to_date
import io.github.vandosketch.camgrid.shared.resources.section_return_to_grid
import io.github.vandosketch.camgrid.shared.resources.section_views
import io.github.vandosketch.camgrid.shared.resources.settings_title
import io.github.vandosketch.camgrid.shared.resources.view_summary
import io.github.vandosketch.camgrid.shared.resources.views_help
import io.github.vandosketch.camgrid.ReturnToGrid
import io.github.vandosketch.camgrid.SourceStatus
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.RemoteConfigException
import io.github.vandosketch.camgrid.core.UrlRedactor
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.vandosketch.camgrid.platform.AutoStart
import io.github.vandosketch.camgrid.platform.AutoStartBlocker

/** Test tag of the Settings screen's list. */
internal const val SETTINGS_LIST_TAG = "settings"

/**
 * Grants the overlay permission from a computer, for devices that hide its system screen (Fire
 * TV). The package is the app's, so it is not translated.
 */
private const val OVERLAY_ADB_COMMAND = "adb shell appops set io.github.vandosketch.camgrid SYSTEM_ALERT_WINDOW allow"

/**
 * Settings, built for the D-pad: Up/Down buttons instead of drag-and-drop for the order of
 * views and cameras. Each view's layout is edited on its own screen. The sections run Cameras,
 * Views, Config URL ([sourceStatus]; while a URL is set, cameras and views are read-only here), Back to grid after inactivity ([returnToGrid], a setting of this device), Start on boot
 * ([autoStart]; hidden where the platform cannot start by itself), Backup and About, which shows [appVersion] (what bug reports should name) and opens the license
 * screen. Each section's rows sit on one card; a row is one lazy item (one card segment), so
 * long lists stay lazy and the D-pad scrolls row by row.
 */
@Composable
fun SettingsScreen(
    config: CamGridConfig,
    onDone: () -> Unit,
    onEditView: (id: String) -> Unit,
    onAddView: () -> Unit,
    onMoveView: (id: String, delta: Int) -> Unit,
    onMoveCamera: (id: String, delta: Int) -> Unit,
    onEditCamera: (id: String?) -> Unit,
    onDeleteCamera: (id: String) -> Unit,
    onImport: () -> Unit,
    onBackup: () -> Unit,
    appVersion: String,
    onOpenLicenses: () -> Unit,
    returnToGrid: ReturnToGrid,
    onReturnToGridChange: (ReturnToGrid) -> Unit,
    autoStart: AutoStart? = null,
    sourceStatus: SourceStatus = SourceStatus.Off,
    onSetUpSource: () -> Unit = {},
    onCheckSourceNow: () -> Unit = {},
) {
    // Cameras and views come from the config URL: shown, not edited (the ViewModel ignores edits too).
    val managed = config.source != null
    var pendingDelete by remember { mutableStateOf<Camera?>(null) }
    val doneRequester = remember { FocusRequester() }
    // The button that opened a sub-screen (Licenses, Backup, an editor) gets the focus back on
    // the return, where the list is still scrolled to; Done may be scrolled out of reach then.
    var opener by rememberSaveable { mutableStateOf<String?>(null) }
    val returnTo = remember { opener }
    if (returnTo == null) InitialFocus(doneRequester)
    val focusReturn = remember { FocusReturn(returnTo) }
    fun open(key: String, action: () -> Unit) {
        opener = key
        action()
    }

    val autoStartSettings = autoStart?.let { remember(it) { AutoStartSettings(it) } }
    if (autoStartSettings != null) {
        // Back from the system permission screen: the blocker may be gone now.
        LifecycleResumeEffect(autoStartSettings) {
            autoStartSettings.refresh()
            onPauseOrDispose { }
        }
    }

    val listState = rememberLazyListState()
    ScrollbarBox(listState, Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(SETTINGS_LIST_TAG),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(Res.string.settings_title),
                    actionLabel = stringResource(Res.string.done),
                    onAction = onDone,
                    actionRequester = doneRequester,
                )
            }

            // First: the cameras are what people come here for.
            item(key = "title:cameras") { SettingsSectionTitle(SettingsIcons.Cameras, stringResource(Res.string.section_cameras)) }
            if (managed) {
                item(key = "cameras:managed") {
                    CardSegment(first = true, last = config.cameras.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.source_managed_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (config.cameras.isEmpty()) {
                // A fresh install lands here: say how to get cameras before the buttons that do it.
                item(key = "cameras:empty") {
                    CardSegment(first = true, last = false) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(Res.string.no_cameras),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(Res.string.no_cameras_help),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // Above the list, so Add stays a press away with many cameras.
            if (!managed) item(key = "cameras:add") {
                CardSegment(first = config.cameras.isNotEmpty(), last = config.cameras.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { open("addCamera") { onEditCamera(null) } },
                            modifier = Modifier.focusBorder(shape = CircleShape).then(focusReturn.modifier("addCamera")),
                        ) {
                            Text(stringResource(Res.string.add_camera))
                        }
                        OutlinedButton(
                            onClick = { open("import", onImport) },
                            modifier = Modifier.focusBorder(shape = CircleShape).then(focusReturn.modifier("import")),
                        ) {
                            Text(stringResource(Res.string.import_go2rtc))
                        }
                    }
                }
            }
            itemsIndexed(config.cameras, key = { _, camera -> camera.id }) { index, camera ->
                CardSegment(first = false, last = index == config.cameras.lastIndex, contentPadding = RowPadding) {
                    CameraRow(
                        position = index + 1,
                        camera = camera,
                        lastPosition = config.cameras.size,
                        editable = !managed,
                        onUp = { onMoveCamera(camera.id, -1) },
                        onDown = { onMoveCamera(camera.id, 1) },
                        onEdit = { open("camera:" + camera.id) { onEditCamera(camera.id) } },
                        editModifier = focusReturn.modifier("camera:" + camera.id),
                        onDelete = { pendingDelete = camera },
                    )
                }
            }

            item(key = "title:views") { SettingsSectionTitle(SettingsIcons.Views, stringResource(Res.string.section_views)) }
            item(key = "views:help") {
                CardSegment(first = true, last = false) {
                    Text(
                        text = stringResource(Res.string.views_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(config.views, key = { _, view -> "view:" + view.id }) { index, view ->
                CardSegment(first = false, last = managed && index == config.views.lastIndex, contentPadding = RowPadding) {
                    ViewRow(
                        position = index + 1,
                        view = view,
                        lastPosition = config.views.size,
                        editable = !managed,
                        onUp = { onMoveView(view.id, -1) },
                        onDown = { onMoveView(view.id, 1) },
                        onEdit = { open("view:" + view.id) { onEditView(view.id) } },
                        editModifier = focusReturn.modifier("view:" + view.id),
                    )
                }
            }
            if (!managed) item(key = "views:add") {
                CardSegment(first = false, last = true) {
                    OutlinedButton(
                        onClick = { open("addView", onAddView) },
                        modifier = Modifier.focusBorder(shape = CircleShape).then(focusReturn.modifier("addView")),
                    ) {
                        Text(stringResource(Res.string.add_view))
                    }
                }
            }

            item(key = "title:source") { SettingsSectionTitle(SettingsIcons.ConfigUrl, stringResource(Res.string.section_config_url)) }
            item(key = "source") {
                CardSegment(first = true, last = true) {
                    ConfigUrlSection(
                        managed = managed,
                        status = sourceStatus,
                        onCheckNow = onCheckSourceNow,
                        onSetUp = { open("source", onSetUpSource) },
                        setUpModifier = focusReturn.modifier("source"),
                    )
                }
            }

            item(key = "title:return") { SettingsSectionTitle(SettingsIcons.Timer, stringResource(Res.string.section_return_to_grid)) }
            item(key = "return") {
                CardSegment(first = true, last = true) {
                    ReturnToGridChoice(selected = returnToGrid, onSelect = onReturnToGridChange)
                }
            }

            if (autoStartSettings != null) autoStartSection(autoStartSettings)

            item(key = "title:backup") { SettingsSectionTitle(SettingsIcons.Backup, stringResource(Res.string.section_backup)) }
            item(key = "backup") {
                CardSegment(first = true, last = true) {
                    OutlinedButton(
                        onClick = { open("backup", onBackup) },
                        modifier = Modifier.focusBorder(shape = CircleShape).then(focusReturn.modifier("backup")),
                    ) {
                        Text(stringResource(Res.string.open_backup))
                    }
                }
            }

            // Last: looked at for bug reports, rarely otherwise.
            item(key = "title:about") { SettingsSectionTitle(Icons.Filled.Info, stringResource(Res.string.section_about)) }
            item(key = "about:version") {
                CardSegment(first = true, last = false) {
                    Column {
                        Text(
                            text = stringResource(Res.string.about_version, appVersion),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(Res.string.about_license),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "about:links") {
                CardSegment(first = false, last = true) {
                    val uriHandler = LocalUriHandler.current
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { open("licenses", onOpenLicenses) },
                            modifier = Modifier.focusBorder(shape = CircleShape).then(focusReturn.modifier("licenses")),
                        ) {
                            Text(stringResource(Res.string.open_licenses))
                        }
                        TextButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(PROJECT_URL)
                                } catch (e: RuntimeException) {
                                    // No browser (Fire TV, ActivityNotFoundException) or no desktop
                                    // integration: the address is on the button to type in elsewhere.
                                }
                            },
                            modifier = Modifier.focusBorder(shape = CircleShape),
                        ) {
                            Text(stringResource(Res.string.about_project_page, PROJECT_URL.removePrefix("https://")))
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { camera ->
        val cancelRequester = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.delete_title)) },
            text = { Text(stringResource(Res.string.delete_message, camera.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCamera(camera.id)
                        pendingDelete = null
                    },
                    modifier = Modifier.focusBorder(shape = CircleShape),
                ) {
                    Text(stringResource(Res.string.delete_confirm))
                }
            },
            dismissButton = {
                // Focused first, so an accidental OK press does not delete anything. The effect
                // lives inside the dialog's content so the button exists when it runs.
                LaunchedEffect(Unit) { cancelRequester.requestFocusAfterLayout() }
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier
                        .focusBorder(shape = CircleShape)
                        .focusRequester(cancelRequester),
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

/**
 * One chip per [ReturnToGrid] choice, wrapping on a narrow phone, and what the choice does. A
 * chip is a single D-pad stop: OK picks it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReturnToGridChoice(selected: ReturnToGrid, onSelect: (ReturnToGrid) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (choice in ReturnToGrid.entries) {
                FilterChip(
                    selected = choice == selected,
                    onClick = { onSelect(choice) },
                    label = {
                        Text(
                            if (choice == ReturnToGrid.OFF) {
                                stringResource(Res.string.return_to_grid_off)
                            } else {
                                stringResource(Res.string.return_to_grid_minutes, choice.minutes)
                            },
                        )
                    },
                    modifier = Modifier.focusBorder(shape = RoundedCornerShape(8.dp)),
                )
            }
        }
        Text(
            text = stringResource(Res.string.return_to_grid_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What the Start on boot section shows. Read when Settings appears or resumes and after each toggle; the
 * platform owns the setting (it is not part of the config or a backup). Kept outside the lazy
 * item, so scrolling the section out and back does not forget that the system screen is missing.
 */
private class AutoStartSettings(private val autoStart: AutoStart) {
    var enabled by mutableStateOf(autoStart.isEnabled())
        private set
    private var blocker by mutableStateOf(autoStart.blocker())

    /** Set when the system screen for the blocker does not exist; then only adb is left. */
    var manual by mutableStateOf(false)
        private set

    val blocked: Boolean get() = enabled && blocker == AutoStartBlocker.OVERLAY_PERMISSION

    fun change(wanted: Boolean) {
        autoStart.setEnabled(wanted)
        enabled = autoStart.isEnabled()
        blocker = autoStart.blocker()
        manual = false
    }

    /** Reads the setting and the blocker again, without forgetting that the system screen is missing. */
    fun refresh() {
        enabled = autoStart.isEnabled()
        blocker = autoStart.blocker()
    }

    fun openBlockerSettings() {
        manual = !autoStart.openBlockerSettings()
    }
}

/**
 * The Start on boot section: a switch row, and while the switch is on but something keeps the
 * start from happening, a note with the way out under it, on the same card.
 */
private fun LazyListScope.autoStartSection(settings: AutoStartSettings) {
    item(key = "title:autostart") { SettingsSectionTitle(SettingsIcons.Boot, stringResource(Res.string.section_autostart)) }
    item(key = "autostart") {
        CardSegment(first = true, last = !settings.blocked, contentPadding = PaddingValues(4.dp)) {
            AutoStartSwitch(checked = settings.enabled, onCheckedChange = settings::change)
        }
        if (settings.blocked) {
            CardSegment(first = false, last = true) {
                OverlayPermissionNote(manual = settings.manual, onOpenSettings = settings::openBlockerSettings)
            }
        }
    }
}

/**
 * The whole row toggles, so OK on the remote works wherever the focus border is; the Switch
 * only shows the state. The help line sits inside, read out with the label.
 */
@Composable
private fun AutoStartSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusBorder()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(Res.string.autostart_switch),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.autostart_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Android 10+ starts an app from the background only with "Display over other apps". The
 * button opens that screen; where the device has none (Fire TV, [manual]), the adb command is
 * the only way, so it is shown to copy from.
 */
@Composable
private fun OverlayPermissionNote(manual: Boolean, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.autostart_overlay_needed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.focusBorder(shape = CircleShape),
        ) {
            Text(stringResource(Res.string.autostart_open_permission))
        }
        if (manual) {
            Text(
                text = stringResource(Res.string.autostart_manual),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = OVERLAY_ADB_COMMAND,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** A list row on a card: the row's own icon buttons bring their padding. */
private val RowPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)

private val CardCorner = 12.dp

/**
 * One lazy item's part of a section card: the card's corners are rounded only on its [first]
 * and [last] segment, and a thin line in the background colour separates a segment from the
 * one above, so a section reads as one card while each row stays its own lazy item.
 */
@Composable
private fun CardSegment(
    first: Boolean,
    last: Boolean,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) CardCorner else 0.dp,
        topEnd = if (first) CardCorner else 0.dp,
        bottomStart = if (last) CardCorner else 0.dp,
        bottomEnd = if (last) CardCorner else 0.dp,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape),
    ) {
        if (!first) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.background,
            )
        }
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/** A Settings section's title with its [icon]; the space above sets the sections apart. */
@Composable
private fun SettingsSectionTitle(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CameraRow(
    position: Int,
    camera: Camera,
    lastPosition: Int,
    editable: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    editModifier: Modifier,
    onDelete: () -> Unit,
) {
    Row(
        modifier = rowModifier(editable),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "$position. ${camera.name}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Credentials are hidden: settings may be open while someone looks at the TV.
            Text(
                text = UrlRedactor.redact(camera.gridUrl),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (editable) {
            OrderButtons(
                name = camera.name,
                position = position,
                lastPosition = lastPosition,
                onUp = onUp,
                onDown = onDown,
            )
            RowIconButton(onEdit, Icons.Filled.Edit, stringResource(Res.string.edit, camera.name), modifier = editModifier)
            RowIconButton(onDelete, Icons.Filled.Delete, stringResource(Res.string.delete, camera.name))
        }
    }
}

@Composable
private fun ViewRow(
    position: Int,
    view: CamView,
    lastPosition: Int,
    editable: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    editModifier: Modifier,
) {
    val name = view.name.ifBlank { view.id }
    Row(
        modifier = rowModifier(editable),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "$position. $name",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.view_summary, view.id, view.tiles.size, view.columns, view.rows),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (editable) {
            OrderButtons(
                name = name,
                position = position,
                lastPosition = lastPosition,
                onUp = onUp,
                onDown = onDown,
            )
            RowIconButton(onEdit, Icons.Filled.Edit, stringResource(Res.string.edit, name), modifier = editModifier)
        }
    }
}

/**
 * A camera or view row. Without its buttons ([editable] false) the row itself is a D-pad stop,
 * read out as one, so the D-pad still steps through the list row by row; OK on it does nothing.
 */
private fun rowModifier(editable: Boolean): Modifier =
    if (editable) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .focusBorder()
            .semantics(mergeDescendants = true) {}
            .focusable()
            // Room between the focus border and the text.
            .padding(horizontal = 8.dp)
    }

/**
 * The Config URL section: one line on where the cameras come from, and the way to set the URL
 * up ([managed] false) or to check it now and change it.
 */
@Composable
private fun ConfigUrlSection(
    managed: Boolean,
    status: SourceStatus,
    onCheckNow: () -> Unit,
    onSetUp: () -> Unit,
    setUpModifier: Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when {
                !managed -> stringResource(Res.string.source_off)
                status == SourceStatus.UpToDate -> stringResource(Res.string.source_up_to_date)
                status is SourceStatus.Failed -> stringResource(Res.string.source_failed, sourceReason(status))
                // Off only for the moment until the first check starts.
                else -> stringResource(Res.string.source_checking)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (status is SourceStatus.Failed && managed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (managed) {
                OutlinedButton(onClick = onCheckNow, modifier = Modifier.focusBorder(shape = CircleShape)) {
                    Text(stringResource(Res.string.source_check_now))
                }
            }
            OutlinedButton(onClick = onSetUp, modifier = Modifier.focusBorder(shape = CircleShape).then(setUpModifier)) {
                Text(stringResource(if (managed) Res.string.source_change else Res.string.source_set_up))
            }
        }
    }
}

/** Why the last check failed, short enough for the middle of [Res.string.source_failed]. */
@Composable
private fun sourceReason(status: SourceStatus.Failed): String = when (status.reason) {
    // "HTTP 401": never the URL or the token; redacted anyway to be safe.
    RemoteConfigException.Reason.HTTP_STATUS -> UrlRedactor.redact(status.detail)
    RemoteConfigException.Reason.NETWORK -> stringResource(Res.string.source_reason_network)
    RemoteConfigException.Reason.TOO_LARGE -> stringResource(Res.string.source_reason_too_large)
    RemoteConfigException.Reason.UNREADABLE -> stringResource(Res.string.source_reason_unreadable)
    RemoteConfigException.Reason.ENCRYPTED -> stringResource(Res.string.source_reason_encrypted)
    RemoteConfigException.Reason.NEWER_VERSION -> stringResource(Res.string.source_reason_newer_version)
    RemoteConfigException.Reason.CREDENTIALS -> stringResource(Res.string.source_reason_credentials)
    RemoteConfigException.Reason.INVALID_URL -> stringResource(Res.string.source_reason_invalid_url)
}

/**
 * Gives the focus back to the button [key]ed [returnTo] once, when Settings comes back from
 * the sub-screen that button opened, in key (D-pad) mode like [InitialFocus]. Lazy rows
 * compose only when scrolled into view, so the button asks for the focus itself when it
 * appears; [done] keeps a row that scrolls out and back in from taking the focus again.
 */
private class FocusReturn(private val returnTo: String?) {
    private var done = returnTo == null

    @Composable
    fun modifier(key: String): Modifier {
        if (key != returnTo) return Modifier
        val requester = remember { FocusRequester() }
        val inputModeManager = LocalInputModeManager.current
        LaunchedEffect(requester) {
            if (!done && inputModeManager.inputMode == InputMode.Keyboard) {
                done = true
                requester.requestFocusAfterLayout()
            }
        }
        return Modifier.focusRequester(requester)
    }
}

/**
 * Up and Down for the row at [position] (1-based) of [lastPosition], disabled where the row
 * cannot move further, so they do not look pressable there (and the D-pad skips them). A press
 * that moves the row to an end hands the focus to the other button first: the pressed one is
 * about to be disabled, and a disabled button would drop the D-pad focus.
 */
@Composable
private fun OrderButtons(
    name: String,
    position: Int,
    lastPosition: Int,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val upRequester = remember { FocusRequester() }
    val downRequester = remember { FocusRequester() }
    var upFocused by remember { mutableStateOf(false) }
    var downFocused by remember { mutableStateOf(false) }
    RowIconButton(
        onClick = {
            if (position == 2 && upFocused) downRequester.tryRequestFocus()
            onUp()
        },
        icon = Icons.Filled.KeyboardArrowUp,
        description = stringResource(Res.string.move_up, name),
        enabled = position > 1,
        modifier = Modifier
            .focusRequester(upRequester)
            .onFocusChanged { upFocused = it.isFocused },
    )
    RowIconButton(
        onClick = {
            if (position == lastPosition - 1 && downFocused) upRequester.tryRequestFocus()
            onDown()
        },
        icon = Icons.Filled.KeyboardArrowDown,
        description = stringResource(Res.string.move_down, name),
        enabled = position < lastPosition,
        modifier = Modifier
            .focusRequester(downRequester)
            .onFocusChanged { downFocused = it.isFocused },
    )
}

@Composable
private fun RowIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.focusBorder(shape = CircleShape).then(modifier),
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

/**
 * Title row with one action button (Done / Back / Save) on the right. [primary] draws it as a
 * filled button, for the action that commits the screen.
 */
@Composable
fun ScreenHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionRequester: FocusRequester? = null,
    primary: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        val requesterModifier =
            if (actionRequester != null) Modifier.focusRequester(actionRequester) else Modifier
        val buttonModifier = Modifier
            .focusBorder(shape = CircleShape)
            .then(requesterModifier)
        if (primary) {
            Button(onClick = onAction, modifier = buttonModifier) { Text(actionLabel) }
        } else {
            OutlinedButton(onClick = onAction, modifier = buttonModifier) { Text(actionLabel) }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp),
    )
}

/**
 * Focuses [requester] when the screen appears, but only in key (D-pad) mode, so D-pad users
 * start inside the screen while touch users do not see a stray focus border.
 */
@Composable
fun InitialFocus(requester: FocusRequester) {
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(requester) {
        if (inputModeManager.inputMode == InputMode.Keyboard) requester.requestFocusAfterLayout()
    }
}
