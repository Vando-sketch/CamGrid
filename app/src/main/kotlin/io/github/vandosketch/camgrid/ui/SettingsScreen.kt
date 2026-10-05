package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.R
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.GridLayout
import io.github.vandosketch.camgrid.core.UrlRedactor

/**
 * Settings, built for the D-pad: steppers instead of sliders, and Up/Down buttons instead of
 * drag-and-drop for the camera order.
 */
@Composable
fun SettingsScreen(
    config: CamGridConfig,
    onDone: () -> Unit,
    onLayoutChange: (columns: Int, rows: Int) -> Unit,
    onMoveCamera: (id: String, delta: Int) -> Unit,
    onEditCamera: (id: String?) -> Unit,
    onDeleteCamera: (id: String) -> Unit,
    onImport: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Camera?>(null) }
    val doneRequester = remember { FocusRequester() }
    InitialFocus(doneRequester)

    val layout = config.layout
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.settings_title),
                actionLabel = stringResource(R.string.done),
                onAction = onDone,
                actionRequester = doneRequester,
            )
        }

        item { SectionTitle(stringResource(R.string.section_layout)) }
        item {
            Stepper(
                label = stringResource(R.string.columns),
                value = layout.columns,
                onValueChange = { onLayoutChange(it, layout.rows) },
            )
        }
        item {
            Stepper(
                label = stringResource(R.string.rows),
                value = layout.rows,
                onValueChange = { onLayoutChange(layout.columns, it) },
            )
        }
        item {
            Text(
                text = stringResource(R.string.tiles_per_page, layout.tilesPerPage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionTitle(stringResource(R.string.section_cameras)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onEditCamera(null) },
                    modifier = Modifier.focusBorder(shape = CircleShape),
                ) {
                    Text(stringResource(R.string.add_camera))
                }
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.focusBorder(shape = CircleShape),
                ) {
                    Text(stringResource(R.string.import_go2rtc))
                }
            }
        }
        if (config.cameras.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_cameras),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(config.cameras, key = { _, camera -> camera.id }) { index, camera ->
            CameraRow(
                position = index + 1,
                camera = camera,
                onUp = { onMoveCamera(camera.id, -1) },
                onDown = { onMoveCamera(camera.id, 1) },
                onEdit = { onEditCamera(camera.id) },
                onDelete = { pendingDelete = camera },
            )
        }
    }

    pendingDelete?.let { camera ->
        val cancelRequester = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, camera.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCamera(camera.id)
                        pendingDelete = null
                    },
                    modifier = Modifier.focusBorder(shape = CircleShape),
                ) {
                    Text(stringResource(R.string.delete_confirm))
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
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CameraRow(
    position: Int,
    camera: Camera,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
        // Buttons stay enabled at the ends of the list: a disabled button would drop D-pad focus.
        RowIconButton(onUp, Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up, camera.name))
        RowIconButton(onDown, Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down, camera.name))
        RowIconButton(onEdit, Icons.Filled.Edit, stringResource(R.string.edit, camera.name))
        RowIconButton(onDelete, Icons.Filled.Delete, stringResource(R.string.delete, camera.name))
    }
}

@Composable
private fun RowIconButton(onClick: () -> Unit, icon: ImageVector, description: String) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.focusBorder(shape = CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

/** A labelled "−  value  +" control; the value is clamped to the grid size limits. */
@Composable
private fun Stepper(label: String, value: Int, onValueChange: (Int) -> Unit) {
    val decreaseDescription = stringResource(R.string.decrease, label)
    val increaseDescription = stringResource(R.string.increase, label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.widthIn(min = 120.dp),
        )
        OutlinedButton(
            onClick = { onValueChange((value - 1).coerceAtLeast(GridLayout.MIN_SIZE)) },
            modifier = Modifier
                .focusBorder(shape = CircleShape)
                .semantics { contentDescription = decreaseDescription },
        ) {
            Text("−")
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        OutlinedButton(
            onClick = { onValueChange((value + 1).coerceAtMost(GridLayout.MAX_SIZE)) },
            modifier = Modifier
                .focusBorder(shape = CircleShape)
                .semantics { contentDescription = increaseDescription },
        ) {
            Text("+")
        }
    }
}

/** Title row with one action button (Done / Back) on the right. */
@Composable
fun ScreenHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionRequester: FocusRequester? = null,
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
        OutlinedButton(
            onClick = onAction,
            modifier = Modifier
                .focusBorder(shape = CircleShape)
                .then(requesterModifier),
        ) {
            Text(actionLabel)
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
