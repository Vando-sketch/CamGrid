package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.Tile
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.ve_camera
import io.github.vandosketch.camgrid.shared.resources.ve_camera_missing
import io.github.vandosketch.camgrid.shared.resources.ve_close
import io.github.vandosketch.camgrid.shared.resources.ve_mode_move
import io.github.vandosketch.camgrid.shared.resources.ve_mode_resize
import io.github.vandosketch.camgrid.shared.resources.ve_remove_tile
import io.github.vandosketch.camgrid.shared.resources.ve_tile_auto
import io.github.vandosketch.camgrid.shared.resources.ve_tile_dialog_title
import org.jetbrains.compose.resources.stringResource

/**
 * Everything about one tile in one place, opened with OK on the preview (or a second tap on
 * the selected tile, or the panel's Change button): the camera list, Crop / Fit, Move, Resize,
 * Remove and Close.
 *
 * The tile's current camera is focused first, so the remote's arrows start from there and OK
 * on it changes nothing. Picking a camera calls [onCamera] and the caller closes the dialog;
 * the picture chips apply at once and leave it open. On wide windows the camera list gets
 * its own column, so a TV shows more than a handful of cameras without scrolling.
 */
@Composable
internal fun TileDialog(
    tileNumber: Int,
    tile: Tile,
    cameras: List<Camera>,
    onCamera: (String?) -> Unit,
    onFit: (FitMode) -> Unit,
    onMove: () -> Unit,
    onResize: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints {
            val wide = maxWidth >= 720.dp
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .testTag(VE_TAG_TILE_DIALOG)
                    .padding(24.dp)
                    .widthIn(max = if (wide) 840.dp else 560.dp)
                    .heightIn(max = maxHeight - 48.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.ve_tile_dialog_title, tileNumber),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    val cameraList = @Composable { modifier: Modifier ->
                        CameraList(tile.camera, cameras, onCamera, modifier)
                    }
                    if (wide) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            cameraList(Modifier.weight(1f))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TileActions(tile, onFit, onMove, onResize, onRemove, onDismiss)
                            }
                        }
                    } else {
                        cameraList(Modifier.weight(1f, fill = false))
                        TileActions(tile, onFit, onMove, onResize, onRemove, onDismiss)
                    }
                }
            }
        }
    }
}

/**
 * Auto first, then every camera in the configured order, as radio rows. A camera that was
 * deleted since stays listed (as the current choice) until another one is picked. Scrolls on
 * its own; a focused row brings itself into view, so the D-pad reaches every camera.
 */
@Composable
private fun CameraList(
    current: String?,
    cameras: List<Camera>,
    onCamera: (String?) -> Unit,
    modifier: Modifier,
) {
    val currentRequester = remember { FocusRequester() }
    // The effect runs inside the dialog's content, so the row exists when it fires.
    LaunchedEffect(Unit) { currentRequester.requestFocusAfterLayout() }
    val missing = current != null && cameras.none { it.id == current }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.ve_camera), style = MaterialTheme.typography.labelLarge)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (missing) {
                CameraRow(stringResource(Res.string.ve_camera_missing), true, currentRequester) { onCamera(current) }
            }
            CameraRow(
                label = stringResource(Res.string.ve_tile_auto),
                selected = current == null,
                requester = currentRequester.takeIf { current == null },
            ) { onCamera(null) }
            for (camera in cameras) {
                CameraRow(
                    label = camera.name,
                    selected = camera.id == current,
                    requester = currentRequester.takeIf { camera.id == current },
                ) { onCamera(camera.id) }
            }
        }
    }
}

@Composable
private fun CameraRow(label: String, selected: Boolean, requester: FocusRequester?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusBorder(shape = RoundedCornerShape(8.dp))
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row is the control; the radio button only shows the state.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Picture chips, then Move / Resize / Remove, then Close. */
@Composable
private fun ColumnScope.TileActions(
    tile: Tile,
    onFit: (FitMode) -> Unit,
    onMove: () -> Unit,
    onResize: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    FitSelector(selected = tile.fit, onSelect = onFit)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onMove, modifier = Modifier.focusBorder(shape = CircleShape)) {
            Text(stringResource(Res.string.ve_mode_move))
        }
        OutlinedButton(onClick = onResize, modifier = Modifier.focusBorder(shape = CircleShape)) {
            Text(stringResource(Res.string.ve_mode_resize))
        }
    }
    OutlinedButton(onClick = onRemove, modifier = Modifier.focusBorder(shape = CircleShape)) {
        Text(stringResource(Res.string.ve_remove_tile))
    }
    TextButton(
        onClick = onDismiss,
        modifier = Modifier
            .align(Alignment.End)
            .focusBorder(shape = CircleShape),
    ) {
        Text(stringResource(Res.string.ve_close))
    }
}
