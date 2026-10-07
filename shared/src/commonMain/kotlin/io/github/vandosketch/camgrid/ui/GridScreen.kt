package io.github.vandosketch.camgrid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.empty_body
import io.github.vandosketch.camgrid.shared.resources.empty_title
import io.github.vandosketch.camgrid.shared.resources.open_settings
import io.github.vandosketch.camgrid.shared.resources.page_indicator
import io.github.vandosketch.camgrid.shared.resources.page_indicator_named
import io.github.vandosketch.camgrid.shared.resources.status_connecting
import io.github.vandosketch.camgrid.shared.resources.status_offline
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.Direction
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.core.GridPosition
import io.github.vandosketch.camgrid.core.Tile
import io.github.vandosketch.camgrid.core.TileNavigator
import io.github.vandosketch.camgrid.core.ViewPaging
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.VideoPlatform
import kotlinx.coroutines.delay

/** Width of the ring around the selected tile. */
private val SelectionWidth = 5.dp

/** Space around every tile. */
private val TileGap = 2.dp

/** Outline of a tile's placeholder and of the rings around a tile. */
private val TileShape = RoundedCornerShape(4.dp)

/** How long the page indicator stays after entering the grid or switching page. */
internal const val PAGE_INDICATOR_MS = 3_000L

/**
 * The camera wall: the config's views one after the other, each as one or more pages of tiles
 * (see [ViewPaging]), playing muted low-res streams. Tiles can have any size on the view's cell
 * canvas, so portrait and landscape tiles can sit side by side.
 *
 * The wall is one focusable container that handles the keys itself; the selected tile is plain
 * state ([focusIndex]), never Compose focus on a tile, so the selection does not depend on focus
 * landing next to a video view. Arrows move it with core's [TileNavigator] (off the left/right
 * edge to the other page, off the top edge to the settings button), OK/Enter opens it, 1-9 open
 * the Nth camera of the page and Page Up/Down (or the channel and track keys) switch page. The
 * selection ring is drawn over all tiles while the wall has focus in key mode
 * ([isKeyboardNavigation]), so phones and mouse users never see it. On touch screens a
 * horizontal swipe switches page and a tap opens the camera; a mouse gets a lighter hover ring
 * and the hand cursor.
 *
 * Tiles whose stream is not playing (connecting, offline) show a lighter placeholder, so the
 * wall does not look like one black area. With several pages, the view name and page number
 * show at the top for [PAGE_INDICATOR_MS] after entering the grid and after every page switch,
 * then fade out, so they do not keep covering the tile beneath.
 */
@Composable
fun GridScreen(
    video: VideoPlatform,
    config: CamGridConfig,
    page: Int,
    focusIndex: Int,
    onPositionChange: (GridPosition) -> Unit,
    onOpenCamera: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (config.cameras.isEmpty()) {
        EmptyGrid(onOpenSettings)
        return
    }
    val pages = remember(config) { ViewPaging.pages(config) }

    val currentPage = page.coerceIn(0, pages.lastIndex)
    val gridPage = pages[currentPage]
    val tiles = gridPage.tiles
    // The remembered index may point at an empty tile (a page switch lands on 0); use the first
    // tile with a camera then. -1 when the page has none.
    val currentIndex = focusIndex.takeIf { tiles.getOrNull(it)?.camera != null }
        ?: tiles.indexOfFirst { it.camera != null }
    val gridRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }
    var gridFocused by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }
    val keyMode = isKeyboardNavigation()
    val showSelection = gridFocused && keyMode && currentIndex >= 0

    // The wall takes the keys on entering the grid and keeps focus across page switches.
    LaunchedEffect(Unit) { gridRequester.tryRequestFocus() }

    val pageCount = pages.size
    val switchPage by rememberUpdatedState { delta: Int ->
        val target = currentPage + delta
        if (target in 0 until pageCount) onPositionChange(GridPosition(target, 0))
    }
    val openTile by rememberUpdatedState { index: Int ->
        val camera = tiles.getOrNull(index)?.camera
        if (camera != null) {
            onPositionChange(GridPosition(currentPage, index))
            onOpenCamera(camera.id)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .keepScreenOn(),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .focusRequester(gridRequester)
                .onFocusChanged { gridFocused = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val key = event.key
                    val direction = key.toDirection()
                    val digit = key.digit()
                    when {
                        direction != null -> {
                            val from = GridPosition(currentPage, currentIndex)
                            val to = if (currentIndex >= 0) TileNavigator.move(pages, from, direction) else from
                            when {
                                to != from -> onPositionChange(to)
                                // Top edge: go up to the settings button in the corner.
                                direction == Direction.UP -> settingsRequester.tryRequestFocus()
                            }
                            true
                        }
                        key.isConfirm() -> {
                            // A held OK must not reach fullscreen as a burst of sound toggles.
                            if (!event.isRepeat) openTile(currentIndex)
                            true
                        }
                        digit != null -> {
                            val index = tiles.indices.filter { tiles[it].camera != null }.getOrNull(digit - 1)
                            if (index != null && !event.isRepeat) openTile(index)
                            true
                        }
                        key.pageDelta() != 0 -> {
                            switchPage(key.pageDelta())
                            true
                        }
                        // Unhandled keys (Esc, ?, F...) go on to the app and the window.
                        else -> false
                    }
                }
                .focusable()
                .pointerInput(Unit) {
                    val threshold = 80.dp.toPx()
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            when {
                                total < -threshold -> switchPage(1)
                                total > threshold -> switchPage(-1)
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                    }
                },
        ) {
            val view = gridPage.view
            val cellWidth = maxWidth / view.columns
            val cellHeight = maxHeight / view.rows
            val slot = { tile: Tile ->
                Modifier
                    .offset(x = cellWidth * tile.x, y = cellHeight * tile.y)
                    .size(width = cellWidth * tile.w, height = cellHeight * tile.h)
                    .padding(TileGap)
            }
            tiles.forEachIndexed { index, placed ->
                Box(slot(placed.tile)) {
                    // A tile showing a different camera after a page switch plays that camera's
                    // stream (streams are kept per camera). A fixed tile that keeps its camera
                    // across pages keeps playing.
                    val camera = placed.camera
                    if (camera != null) {
                        val ringed = showSelection && index == currentIndex
                        CameraTile(
                            video = video,
                            camera = camera,
                            fit = placed.tile.fit,
                            // A camera on two tiles: a stream draws into one view at a time.
                            firstTileOfCamera = tiles.indexOfFirst { it.camera?.id == camera.id } == index,
                            selected = index == currentIndex,
                            // Inside the ring, so the ring never covers the video, even where a
                            // video view draws over the window's content.
                            modifier = Modifier.padding(if (ringed) SelectionWidth + 1.dp else 0.dp),
                            onHoverChange = { hovered ->
                                if (hovered) {
                                    hoveredIndex = index
                                } else if (hoveredIndex == index) {
                                    hoveredIndex = -1
                                }
                            },
                            onClick = { openTile(index) },
                        )
                    }
                }
            }

            // The rings come last, so they are drawn over every tile.
            val hovered = tiles.getOrNull(hoveredIndex)?.takeIf { it.camera != null }
            if (hovered != null && !(showSelection && hoveredIndex == currentIndex)) {
                Box(slot(hovered.tile).border(3.dp, Color.White.copy(alpha = 0.7f), TileShape))
            }
            if (showSelection) {
                Box(slot(tiles[currentIndex].tile).border(SelectionWidth, FocusColor, TileShape))
            }
        }

        if (pageCount > 1) {
            // Shown on entering the grid and on every page switch, then faded out: it sits on
            // top of a tile, and the page is only news right after it changed.
            var indicatorVisible by remember { mutableStateOf(true) }
            LaunchedEffect(currentPage) {
                indicatorVisible = true
                delay(PAGE_INDICATOR_MS)
                indicatorVisible = false
            }
            val viewName = gridPage.view.name
            val position = stringResource(Res.string.page_indicator, currentPage + 1, pageCount)
            // Top centre: tile names sit bottom-left in every tile, so a bottom indicator would
            // cover the name of whichever tile ends there.
            AnimatedVisibility(
                visible = indicatorVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Text(
                    text = if (viewName.isBlank()) position else stringResource(Res.string.page_indicator_named, viewName, position),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .focusBorder(width = 3.dp, shape = CircleShape)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .focusRequester(settingsRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        // Back into the grid, to the tile that was selected before.
                        Key.DirectionDown, Key.DirectionLeft -> {
                            gridRequester.tryRequestFocus()
                            true
                        }
                        Key.DirectionUp, Key.DirectionRight -> true
                        else -> false
                    }
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(Res.string.open_settings),
                tint = Color.White,
            )
        }
    }
}

/**
 * One camera on the wall. Clickable for touch and mouse but never focusable: the wall's
 * container owns the keys, and [selected] is its state (exposed to accessibility and tests).
 * Plays the camera's stream from [LocalGridStreams] on its [firstTileOfCamera], so it survives a
 * visit to fullscreen; opens its own stream otherwise.
 */
@Composable
private fun CameraTile(
    video: VideoPlatform,
    camera: Camera,
    fit: FitMode,
    firstTileOfCamera: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onHoverChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val gridStreams = LocalGridStreams.current
    val stream = if (firstTileOfCamera && camera.id in gridStreams) {
        gridStreams[camera.id]
    } else {
        video.rememberLiveStream(camera.gridUrl, camera.streamType, camera.name, audioEnabled = false)
    }
    val status = stream?.status ?: StreamStatus.Connecting
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val currentOnHoverChange by rememberUpdatedState(onHoverChange)
    LaunchedEffect(hovered) { currentOnHoverChange(hovered) }
    Box(
        modifier
            .fillMaxSize()
            .semantics { this.selected = selected }
            .focusProperties { canFocus = false }
            .hoverable(hoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .background(Color.Black),
    ) {
        video.Surface(stream, Modifier.fillMaxSize(), fit)
        if (status !is StreamStatus.Playing) {
            // Over the surface, which is black until there is a picture: a lighter placeholder
            // with rounded corners, so the tiles stand apart (the black gap between them shows).
            // Gone once the stream plays, so it never touches the video.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, TileShape))
        }
        StreamStatusBadge(
            status = status,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            text = camera.name,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** "Connecting…" or "Offline · retry in N s" over a stream; nothing while it plays. */
@Composable
fun StreamStatusBadge(status: StreamStatus, modifier: Modifier = Modifier) {
    if (status is StreamStatus.Playing) return
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (status) {
            is StreamStatus.Offline -> {
                Text(
                    text = stringResource(Res.string.status_offline, status.retryInSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                // An error code from the player, never a URL.
                Text(
                    text = status.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            else -> Text(
                text = stringResource(Res.string.status_connecting),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun EmptyGrid(onOpenSettings: () -> Unit) {
    val buttonRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { buttonRequester.tryRequestFocus() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .focusBorder(shape = CircleShape)
                .focusRequester(buttonRequester),
        ) {
            Text(stringResource(Res.string.open_settings))
        }
    }
}

private fun Key.toDirection(): Direction? = when (this) {
    Key.DirectionUp -> Direction.UP
    Key.DirectionDown -> Direction.DOWN
    Key.DirectionLeft -> Direction.LEFT
    Key.DirectionRight -> Direction.RIGHT
    else -> null
}
