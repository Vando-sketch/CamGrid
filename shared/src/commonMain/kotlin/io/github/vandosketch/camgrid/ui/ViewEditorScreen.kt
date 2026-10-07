package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.ve_add_tile
import io.github.vandosketch.camgrid.shared.resources.ve_arrow_down
import io.github.vandosketch.camgrid.shared.resources.ve_arrow_left
import io.github.vandosketch.camgrid.shared.resources.ve_arrow_right
import io.github.vandosketch.camgrid.shared.resources.ve_arrow_up
import io.github.vandosketch.camgrid.shared.resources.ve_camera
import io.github.vandosketch.camgrid.shared.resources.ve_camera_missing
import io.github.vandosketch.camgrid.shared.resources.ve_cancel
import io.github.vandosketch.camgrid.shared.resources.ve_change_camera
import io.github.vandosketch.camgrid.shared.resources.ve_columns
import io.github.vandosketch.camgrid.shared.resources.ve_decrease
import io.github.vandosketch.camgrid.shared.resources.ve_delete_confirm
import io.github.vandosketch.camgrid.shared.resources.ve_delete_message
import io.github.vandosketch.camgrid.shared.resources.ve_delete_title
import io.github.vandosketch.camgrid.shared.resources.ve_delete_view
import io.github.vandosketch.camgrid.shared.resources.ve_done
import io.github.vandosketch.camgrid.shared.resources.ve_fill_empty
import io.github.vandosketch.camgrid.shared.resources.ve_fit
import io.github.vandosketch.camgrid.shared.resources.ve_fit_crop
import io.github.vandosketch.camgrid.shared.resources.ve_fit_fit
import io.github.vandosketch.camgrid.shared.resources.ve_help_camera_auto
import io.github.vandosketch.camgrid.shared.resources.ve_help_fit_crop
import io.github.vandosketch.camgrid.shared.resources.ve_help_fit_fit
import io.github.vandosketch.camgrid.shared.resources.ve_hint_move
import io.github.vandosketch.camgrid.shared.resources.ve_hint_resize
import io.github.vandosketch.camgrid.shared.resources.ve_hint_select
import io.github.vandosketch.camgrid.shared.resources.ve_increase
import io.github.vandosketch.camgrid.shared.resources.ve_mode_current
import io.github.vandosketch.camgrid.shared.resources.ve_mode_move
import io.github.vandosketch.camgrid.shared.resources.ve_mode_resize
import io.github.vandosketch.camgrid.shared.resources.ve_mode_select
import io.github.vandosketch.camgrid.shared.resources.ve_move_done
import io.github.vandosketch.camgrid.shared.resources.ve_name
import io.github.vandosketch.camgrid.shared.resources.ve_name_hint
import io.github.vandosketch.camgrid.shared.resources.ve_no_tiles
import io.github.vandosketch.camgrid.shared.resources.ve_preset_grid_2x2
import io.github.vandosketch.camgrid.shared.resources.ve_preset_grid_3x3
import io.github.vandosketch.camgrid.shared.resources.ve_preset_one_big_five_small
import io.github.vandosketch.camgrid.shared.resources.ve_preset_one_big_three_small
import io.github.vandosketch.camgrid.shared.resources.ve_preset_side_by_side
import io.github.vandosketch.camgrid.shared.resources.ve_preset_three_portrait
import io.github.vandosketch.camgrid.shared.resources.ve_preset_two_portrait_two_landscape
import io.github.vandosketch.camgrid.shared.resources.ve_preview_description
import io.github.vandosketch.camgrid.shared.resources.ve_remove_tile
import io.github.vandosketch.camgrid.shared.resources.ve_rows
import io.github.vandosketch.camgrid.shared.resources.ve_section_layout
import io.github.vandosketch.camgrid.shared.resources.ve_section_presets
import io.github.vandosketch.camgrid.shared.resources.ve_section_tile
import io.github.vandosketch.camgrid.shared.resources.ve_section_view
import io.github.vandosketch.camgrid.shared.resources.ve_stream_count
import io.github.vandosketch.camgrid.shared.resources.ve_stream_warning
import io.github.vandosketch.camgrid.shared.resources.ve_tile_auto
import io.github.vandosketch.camgrid.shared.resources.ve_tile_number
import io.github.vandosketch.camgrid.shared.resources.ve_title
import io.github.vandosketch.camgrid.shared.resources.ve_view_id
import io.github.vandosketch.camgrid.core.CamView
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.Direction
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.Tile
import io.github.vandosketch.camgrid.core.TileNavigator
import io.github.vandosketch.camgrid.core.ViewEditor
import io.github.vandosketch.camgrid.core.ViewPreset

/** About how many streams a Fire TV Stick decodes at once before playback starts failing. */
private const val STICK_STREAM_LIMIT = 4

/** Rotations that turn the up arrow icon into a left or right arrow. */
private const val LEFT_DEGREES = -90f
private const val RIGHT_DEGREES = 90f

/** What the arrows on the preview do: pick a tile, or change the selected one. */
private enum class EditMode {
    SELECT,
    MOVE,
    RESIZE,
}

/** Where focus goes back to when the tile dialog closes: the control that opened it. */
private enum class DialogOpener {
    PREVIEW,
    PANEL,
    TOUCH,
}

/**
 * Layout editor for one [CamView], built for the Fire TV remote first and touch second.
 *
 * The preview is a single focusable element that takes the arrows itself. In Select mode
 * they pick a tile (and let focus leave at the edges, Right into the "Selected tile" section)
 * and OK opens the [TileDialog] for that tile: camera, picture, Move, Resize, Remove. Move and
 * Resize put the preview into that mode, where the arrows change the tile until OK or Back.
 * On touch a tap selects a tile and a second tap opens the same dialog; the arrow pad for
 * moving and resizing appears in the panel only while one of those modes is on.
 *
 * The side panel groups the settings as "Selected tile", "Layout" (whole canvas) and "View"
 * (name, id, delete). [view] is the live, persisted state; every edit goes out through
 * [onChange] as a new view built with core's [ViewEditor], which ignores edits that would
 * break the geometry.
 */
@Composable
fun ViewEditorScreen(
    view: CamView,
    cameras: List<Camera>,
    canDelete: Boolean,
    onChange: (CamView) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(EditMode.SELECT) }
    var dialogOpener by remember { mutableStateOf<DialogOpener?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // A Back press handled on the preview; its key-up is swallowed too (see onPreviewKey).
    var backDown by remember { mutableStateOf(false) }
    val previewRequester = remember { FocusRequester() }
    val changeCameraRequester = remember { FocusRequester() }
    // Set to move focus once the next frame is laid out (after a dialog closes, for example).
    var pendingFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val inputModeManager = LocalInputModeManager.current
    InitialFocus(previewRequester)
    LaunchedEffect(pendingFocus) {
        pendingFocus?.requestFocusAfterLayout()
        pendingFocus = null
    }

    // Back leaves Move/Resize first; in Select mode the caller's handler closes the editor.
    // The preview handles the key itself; this covers the gesture and focus elsewhere.
    BackHandler(enabled = mode != EditMode.SELECT) { mode = EditMode.SELECT }

    // Removing tiles, shrinking the canvas or a preset can leave fewer tiles than before.
    val selectedIndex = if (view.tiles.isEmpty()) -1 else selected.coerceIn(0, view.tiles.lastIndex)

    // ViewEditor returns the same instance for an impossible edit: nothing to save then.
    fun edit(next: CamView) {
        if (next !== view) onChange(next)
    }

    fun removeSelected() {
        val next = ViewEditor.removeTile(view, selectedIndex)
        if (next !== view) {
            onChange(next)
            selected = selectedIndex.coerceAtMost(next.tiles.lastIndex).coerceAtLeast(0)
        }
    }

    fun openDialog(opener: DialogOpener) {
        if (selectedIndex < 0) return
        mode = EditMode.SELECT
        dialogOpener = opener
    }

    // Focus goes back to whatever opened the dialog; a tap opened it, so nothing had focus.
    fun closeDialog() {
        pendingFocus = when (dialogOpener) {
            DialogOpener.PREVIEW -> previewRequester
            DialogOpener.PANEL -> changeCameraRequester
            DialogOpener.TOUCH, null -> null
        }
        dialogOpener = null
    }

    // Move/Resize hand the arrows to the preview, so the preview needs focus for the remote
    // (from the dialog, or from the panel's chips when the D-pad is in use).
    fun startMode(next: EditMode, focusPreview: Boolean) {
        mode = next
        if (focusPreview) pendingFocus = previewRequester
    }

    // Shared by the remote (preview key handler) and the touch arrow pad. False means the
    // press was not used, so the focus system may move focus out of the preview instead.
    fun arrow(direction: Direction): Boolean {
        if (selectedIndex < 0) return false
        when (mode) {
            EditMode.SELECT ->
                selected = TileNavigator.neighbor(view.tiles, selectedIndex, direction) ?: return false
            EditMode.MOVE -> edit(ViewEditor.moveTile(view, selectedIndex, direction.dx, direction.dy))
            // Right/Down grow, Left/Up shrink: the right and bottom edges move, like moving does.
            EditMode.RESIZE -> edit(ViewEditor.resizeTile(view, selectedIndex, direction.dx, direction.dy))
        }
        return true
    }

    fun onPreviewKey(event: KeyEvent): Boolean {
        val down = event.type == KeyEventType.KeyDown
        return when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                // A held OK repeats KeyDown; only the first press acts.
                if (down && !event.isRepeat) {
                    if (mode == EditMode.SELECT) openDialog(DialogOpener.PREVIEW) else mode = EditMode.SELECT
                }
                down
            }
            // Handled here and not only by BackHandler, because a TV delivers Back as a key to
            // the focused element first (and the desktop tests can only send keys). The key-up
            // of that press is swallowed too, so it cannot close the editor afterwards.
            Key.Back, Key.Escape -> when {
                down && mode != EditMode.SELECT -> {
                    mode = EditMode.SELECT
                    backDown = true
                    true
                }
                !down && backDown -> {
                    backDown = false
                    true
                }
                else -> false
            }
            else -> down && event.key.toEditorDirection()?.let { arrow(it) } == true
        }
    }

    val keyMode = { inputModeManager.inputMode == InputMode.Keyboard }
    val preview = @Composable { wide: Boolean ->
        LayoutPreview(
            view = view,
            cameras = cameras,
            selectedIndex = selectedIndex,
            focusRequester = previewRequester,
            // Right from the preview's right edge (wide) or Down from its bottom (one column)
            // lands on the first control of the "Selected tile" section, not anywhere nearby.
            exitRequester = changeCameraRequester.takeIf { selectedIndex >= 0 },
            exitRight = wide,
            onTapTile = { hit ->
                if (hit == selectedIndex) openDialog(DialogOpener.TOUCH) else selected = hit
            },
            onKey = { onPreviewKey(it) },
        )
        ModeLine(mode)
    }
    val sections = @Composable {
        SelectedTileSection(
            view = view,
            cameras = cameras,
            selectedIndex = selectedIndex,
            mode = mode,
            changeCameraRequester = changeCameraRequester,
            onChangeCamera = { openDialog(DialogOpener.PANEL) },
            onEdit = { edit(it) },
            onModeChange = { next ->
                if (next == mode) mode = EditMode.SELECT else startMode(next, focusPreview = keyMode())
            },
            onArrow = { arrow(it) },
            onRemove = { removeSelected() },
        )
        LayoutSection(
            view = view,
            onEdit = { edit(it) },
            onSelect = { selected = it },
        )
        ViewSection(
            view = view,
            canDelete = canDelete,
            onNameChange = { onChange(view.copy(name = it)) },
            onRequestDelete = { confirmDelete = true },
        )
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        if (maxWidth >= 600.dp) {
            // TV and landscape tablets: preview on the left, settings scroll on the right.
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                EditorHeader(onDone)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    val previewScroll = rememberScrollState()
                    ScrollbarBox(previewScroll, Modifier.weight(1.3f).fillMaxHeight()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(previewScroll),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            preview(true)
                        }
                    }
                    // The panel is often taller than the window (720 px on a small desktop
                    // window): it scrolls on its own, with a scrollbar on the desktop. The end
                    // padding keeps the controls clear of that scrollbar.
                    val panelScroll = rememberScrollState()
                    ScrollbarBox(panelScroll, Modifier.weight(1f).fillMaxHeight()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(panelScroll)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            sections()
                        }
                    }
                }
            }
        } else {
            // Phones in portrait: everything in one scrolling column.
            val scroll = rememberScrollState()
            ScrollbarBox(scroll, Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorHeader(onDone)
                    preview(false)
                    sections()
                }
            }
        }
    }

    val dialogTile = view.tiles.getOrNull(selectedIndex)
    if (dialogOpener != null && dialogTile != null) {
        TileDialog(
            tileNumber = selectedIndex + 1,
            tile = dialogTile,
            cameras = cameras,
            onCamera = {
                edit(ViewEditor.setTileCamera(view, selectedIndex, it))
                closeDialog()
            },
            onFit = { edit(ViewEditor.setTileFit(view, selectedIndex, it)) },
            onMove = {
                dialogOpener = null
                startMode(EditMode.MOVE, focusPreview = true)
            },
            onResize = {
                dialogOpener = null
                startMode(EditMode.RESIZE, focusPreview = true)
            },
            onRemove = {
                removeSelected()
                closeDialog()
            },
            onDismiss = { closeDialog() },
        )
    }

    if (confirmDelete) {
        DeleteViewDialog(
            viewName = view.name.ifBlank { view.id },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun EditorHeader(onDone: () -> Unit) {
    ScreenHeader(
        title = stringResource(Res.string.ve_title),
        actionLabel = stringResource(Res.string.ve_done),
        onAction = onDone,
    )
}

/**
 * The view drawn to scale on a box shaped like this screen: faint cell grid, one rectangle per
 * tile. One focusable element; [onKey] gets the remote's keys, a tap reports the tile under it.
 * When [onKey] lets an arrow through, Right ([exitRight]) or Down goes to [exitRequester].
 */
@Composable
private fun LayoutPreview(
    view: CamView,
    cameras: List<Camera>,
    selectedIndex: Int,
    focusRequester: FocusRequester,
    exitRequester: FocusRequester?,
    exitRight: Boolean,
    onTapTile: (Int) -> Unit,
    onKey: (KeyEvent) -> Boolean,
) {
    // The canvas is stretched over the whole window, so the preview has the window's shape.
    val windowSize = LocalWindowInfo.current.containerSize
    val aspect = windowSize.width.toFloat() / windowSize.height.coerceAtLeast(1)
    val description = stringResource(Res.string.ve_preview_description)
    val lineColor = Color.White.copy(alpha = 0.15f)
    val columns = view.columns
    val rows = view.rows
    // The tap handler changes with the selection; the gesture detector must not restart for it.
    val currentOnTapTile by rememberUpdatedState(onTapTile)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.4f, 2.5f))
            .testTag(VE_TAG_PREVIEW)
            .focusBorder(shape = RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .focusProperties {
                if (exitRequester != null) {
                    if (exitRight) right = exitRequester else down = exitRequester
                }
            }
            .onPreviewKeyEvent(onKey)
            .semantics { contentDescription = description }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            // Room for the focus border, so it does not cover the outer tiles.
            .padding(6.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                for (c in 1 until columns) {
                    val x = size.width * c / columns
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), stroke)
                }
                for (r in 1 until rows) {
                    val y = size.height * r / rows
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke)
                }
            }
            .pointerInput(view) {
                detectTapGestures { offset ->
                    val cx = (offset.x / size.width * view.columns).toInt()
                    val cy = (offset.y / size.height * view.rows).toInt()
                    val hit = view.tiles.indexOfFirst { cx in it.x until it.right && cy in it.y until it.bottom }
                    if (hit >= 0) currentOnTapTile(hit)
                }
            },
    ) {
        val cellWidth = maxWidth / columns
        val cellHeight = maxHeight / rows
        view.tiles.forEachIndexed { index, tile ->
            PreviewTile(
                tile = tile,
                label = cameraLabel(tile.camera, cameras),
                selected = index == selectedIndex,
                modifier = Modifier
                    .offset(x = cellWidth * tile.x, y = cellHeight * tile.y)
                    .size(width = cellWidth * tile.w, height = cellHeight * tile.h)
                    .testTag(veTileTag(index)),
            )
        }
    }
}

@Composable
private fun PreviewTile(tile: Tile, label: String, selected: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val colors = MaterialTheme.colorScheme
    val auto = tile.camera == null
    val textColor = if (auto) colors.onSecondaryContainer else colors.onPrimaryContainer
    Box(
        modifier
            .semantics { this.selected = selected }
            .padding(2.dp)
            .background(if (auto) colors.secondaryContainer else colors.primaryContainer, shape)
            .border(
                BorderStroke(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) colors.primary else colors.outline,
                ),
                shape,
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(tile.fit.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ModeLine(mode: EditMode) {
    Column {
        Text(
            text = stringResource(Res.string.ve_mode_current, stringResource(mode.labelRes)),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(mode.hintRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The selected tile's settings: camera (with Change… for the [TileDialog]), picture, Move /
 * Resize (with the touch arrow pad while one is on) and Remove. Change… comes first and takes
 * [changeCameraRequester], so Right from the preview lands on it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedTileSection(
    view: CamView,
    cameras: List<Camera>,
    selectedIndex: Int,
    mode: EditMode,
    changeCameraRequester: FocusRequester,
    onChangeCamera: () -> Unit,
    onEdit: (CamView) -> Unit,
    onModeChange: (EditMode) -> Unit,
    onArrow: (Direction) -> Unit,
    onRemove: () -> Unit,
) {
    SectionTitle(stringResource(Res.string.ve_section_tile))
    val tile = view.tiles.getOrNull(selectedIndex)
    if (tile == null) {
        Text(
            text = stringResource(Res.string.ve_no_tiles),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        text = stringResource(Res.string.ve_tile_number, selectedIndex + 1, view.tiles.size),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.ve_camera),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        Text(
            text = cameraLabel(tile.camera, cameras),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp),
        )
        OutlinedButton(
            onClick = onChangeCamera,
            modifier = Modifier
                .testTag(VE_TAG_CHANGE_CAMERA)
                .focusBorder(shape = CircleShape)
                .focusRequester(changeCameraRequester),
        ) {
            Text(stringResource(Res.string.ve_change_camera))
        }
    }
    if (tile.camera == null) {
        Text(
            text = stringResource(Res.string.ve_help_camera_auto),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    FitSelector(
        selected = tile.fit,
        onSelect = { onEdit(ViewEditor.setTileFit(view, selectedIndex, it)) },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Selecting the chip that is on again goes back to Select.
        for (option in listOf(EditMode.MOVE, EditMode.RESIZE)) {
            FilterChip(
                selected = option == mode,
                onClick = { onModeChange(option) },
                label = { Text(stringResource(option.labelRes)) },
                modifier = Modifier.focusBorder(shape = RoundedCornerShape(8.dp)),
            )
        }
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.focusBorder(shape = CircleShape),
        ) {
            Text(stringResource(Res.string.ve_remove_tile))
        }
    }
    // For touch; on the remote the preview takes the arrows itself while moving or resizing.
    if (mode != EditMode.SELECT) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArrowPad(onArrow)
            TextButton(
                onClick = { onModeChange(mode) },
                modifier = Modifier
                    .padding(start = 16.dp)
                    .focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(Res.string.ve_move_done))
            }
        }
    }
}

/** The whole canvas: presets, columns and rows, adding and filling tiles, and the stream count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutSection(view: CamView, onEdit: (CamView) -> Unit, onSelect: (Int) -> Unit) {
    SectionTitle(stringResource(Res.string.ve_section_layout))
    Text(stringResource(Res.string.ve_section_presets), style = MaterialTheme.typography.labelLarge)
    // Wraps instead of scrolling sideways: a row that runs off the panel's edge hides buttons.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (preset in ViewPreset.entries) {
            OutlinedButton(
                onClick = { onEdit(ViewEditor.applyPreset(view, preset)) },
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(preset.labelRes))
            }
        }
    }
    Stepper(
        label = stringResource(Res.string.ve_columns),
        value = view.columns,
        onValueChange = { onEdit(ViewEditor.setCanvas(view, it, view.rows)) },
    )
    Stepper(
        label = stringResource(Res.string.ve_rows),
        value = view.rows,
        onValueChange = { onEdit(ViewEditor.setCanvas(view, view.columns, it)) },
    )
    // Both stay visible (and do nothing when impossible), so D-pad focus never drops.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedButton(
            onClick = {
                val next = ViewEditor.addTile(view)
                if (next !== view) {
                    onEdit(next)
                    onSelect(next.tiles.lastIndex)
                }
            },
            modifier = Modifier.focusBorder(shape = CircleShape),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(Res.string.ve_add_tile), Modifier.padding(start = 4.dp))
        }
        OutlinedButton(
            onClick = { onEdit(ViewEditor.fillEmpty(view)) },
            modifier = Modifier.focusBorder(shape = CircleShape),
        ) {
            Text(stringResource(Res.string.ve_fill_empty))
        }
    }
    StreamCount(view.tiles.size)
}

/** The view as a whole: name, id and delete. */
@Composable
private fun ViewSection(
    view: CamView,
    canDelete: Boolean,
    onNameChange: (String) -> Unit,
    onRequestDelete: () -> Unit,
) {
    SectionTitle(stringResource(Res.string.ve_section_view))
    CamTextField(
        value = view.name,
        onValueChange = onNameChange,
        label = { Text(stringResource(Res.string.ve_name)) },
        placeholder = { Text(stringResource(Res.string.ve_name_hint)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusBorder(shape = RoundedCornerShape(4.dp)),
    )
    // Shown because a later version selects views by id (for example per device).
    Text(
        text = stringResource(Res.string.ve_view_id, view.id),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (canDelete) {
        OutlinedButton(
            onClick = onRequestDelete,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .padding(top = 8.dp)
                .focusBorder(shape = CircleShape),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text(stringResource(Res.string.ve_delete_view), Modifier.padding(start = 4.dp))
        }
    }
}

/** Four arrow buttons in a cross that do what the remote's arrows do on the preview. */
@Composable
private fun ArrowPad(onArrow: (Direction) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ArrowButton(Icons.Filled.KeyboardArrowUp, stringResource(Res.string.ve_arrow_up)) { onArrow(Direction.UP) }
        // The gap is one button wide, so the four buttons form a cross.
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            ArrowButton(Icons.Filled.KeyboardArrowUp, stringResource(Res.string.ve_arrow_left), LEFT_DEGREES) {
                onArrow(Direction.LEFT)
            }
            ArrowButton(Icons.Filled.KeyboardArrowUp, stringResource(Res.string.ve_arrow_right), RIGHT_DEGREES) {
                onArrow(Direction.RIGHT)
            }
        }
        ArrowButton(Icons.Filled.KeyboardArrowDown, stringResource(Res.string.ve_arrow_down)) { onArrow(Direction.DOWN) }
    }
}

/**
 * Left and right are the up arrow turned by [degrees]: the core Left/Right arrows are deprecated
 * for auto-mirrored ones, which would point the wrong way in RTL for a screen direction.
 */
@Composable
private fun ArrowButton(icon: ImageVector, description: String, degrees: Float = 0f, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.focusBorder(shape = CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.rotate(degrees))
    }
}

/** "Picture" with Crop / Fit chips and a line on what the selected one does. */
@Composable
internal fun FitSelector(selected: FitMode, onSelect: (FitMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.ve_fit), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (fit in listOf(FitMode.CROP, FitMode.FIT)) {
                FilterChip(
                    selected = fit == selected,
                    onClick = { onSelect(fit) },
                    label = { Text(stringResource(fit.labelRes)) },
                    modifier = Modifier.focusBorder(shape = RoundedCornerShape(8.dp)),
                )
            }
        }
        Text(
            text = stringResource(selected.helpRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A labelled "−  value  +" control, clamped to the allowed canvas size. */
@Composable
private fun Stepper(label: String, value: Int, onValueChange: (Int) -> Unit) {
    val decreaseDescription = stringResource(Res.string.ve_decrease, label)
    val increaseDescription = stringResource(Res.string.ve_increase, label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        OutlinedButton(
            onClick = { onValueChange((value - 1).coerceAtLeast(CamView.MIN_CELLS)) },
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
            onClick = { onValueChange((value + 1).coerceAtMost(CamView.MAX_CELLS)) },
            modifier = Modifier
                .focusBorder(shape = CircleShape)
                .semantics { contentDescription = increaseDescription },
        ) {
            Text("+")
        }
    }
}

/** Every tile plays a stream (auto tiles too), so the count is simply the number of tiles. */
@Composable
private fun StreamCount(count: Int) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = pluralStringResource(Res.plurals.ve_stream_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (count > STICK_STREAM_LIMIT) {
            Text(
                text = stringResource(Res.string.ve_stream_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DeleteViewDialog(viewName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.ve_delete_title)) },
        text = { Text(stringResource(Res.string.ve_delete_message, viewName)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.focusBorder(shape = CircleShape),
            ) {
                Text(stringResource(Res.string.ve_delete_confirm))
            }
        },
        dismissButton = {
            // Focused first, so an accidental OK press does not delete anything. The effect
            // lives inside the dialog's content so the button exists when it runs.
            LaunchedEffect(Unit) { cancelRequester.requestFocusAfterLayout() }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .focusBorder(shape = CircleShape)
                    .focusRequester(cancelRequester),
            ) {
                Text(stringResource(Res.string.ve_cancel))
            }
        },
    )
}

/** Auto for an auto tile, else the camera's name (or a placeholder if it was deleted). */
@Composable
private fun cameraLabel(cameraId: String?, cameras: List<Camera>): String =
    if (cameraId == null) {
        stringResource(Res.string.ve_tile_auto)
    } else {
        cameras.firstOrNull { it.id == cameraId }?.name ?: stringResource(Res.string.ve_camera_missing)
    }

private fun Key.toEditorDirection(): Direction? = when (this) {
    Key.DirectionUp -> Direction.UP
    Key.DirectionDown -> Direction.DOWN
    Key.DirectionLeft -> Direction.LEFT
    Key.DirectionRight -> Direction.RIGHT
    else -> null
}

private val Direction.dx: Int
    get() = when (this) {
        Direction.LEFT -> -1
        Direction.RIGHT -> 1
        else -> 0
    }

private val Direction.dy: Int
    get() = when (this) {
        Direction.UP -> -1
        Direction.DOWN -> 1
        else -> 0
    }

private val EditMode.labelRes: StringResource
    get() = when (this) {
        EditMode.SELECT -> Res.string.ve_mode_select
        EditMode.MOVE -> Res.string.ve_mode_move
        EditMode.RESIZE -> Res.string.ve_mode_resize
    }

private val EditMode.hintRes: StringResource
    get() = when (this) {
        EditMode.SELECT -> Res.string.ve_hint_select
        EditMode.MOVE -> Res.string.ve_hint_move
        EditMode.RESIZE -> Res.string.ve_hint_resize
    }

private val FitMode.labelRes: StringResource
    get() = when (this) {
        FitMode.CROP -> Res.string.ve_fit_crop
        FitMode.FIT -> Res.string.ve_fit_fit
    }

private val FitMode.helpRes: StringResource
    get() = when (this) {
        FitMode.CROP -> Res.string.ve_help_fit_crop
        FitMode.FIT -> Res.string.ve_help_fit_fit
    }

private val ViewPreset.labelRes: StringResource
    get() = when (this) {
        ViewPreset.GRID_2X2 -> Res.string.ve_preset_grid_2x2
        ViewPreset.GRID_3X3 -> Res.string.ve_preset_grid_3x3
        ViewPreset.SIDE_BY_SIDE -> Res.string.ve_preset_side_by_side
        ViewPreset.TWO_PORTRAIT_TWO_LANDSCAPE -> Res.string.ve_preset_two_portrait_two_landscape
        ViewPreset.ONE_BIG_THREE_SMALL -> Res.string.ve_preset_one_big_three_small
        ViewPreset.ONE_BIG_FIVE_SMALL -> Res.string.ve_preset_one_big_five_small
        ViewPreset.THREE_PORTRAIT -> Res.string.ve_preset_three_portrait
    }
