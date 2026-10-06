package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalInputModeManager
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
import io.github.vandosketch.camgrid.core.TileNavigator
import io.github.vandosketch.camgrid.core.ViewPaging
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.VideoPlatform

/**
 * The camera wall: the config's views one after the other, each as one or more pages of tiles
 * (see [ViewPaging]), playing muted low-res streams. Tiles can have any size on the view's cell
 * canvas, so portrait and landscape tiles can sit side by side.
 *
 * D-pad arrows are handled here (not by Compose's default focus search) with core's
 * [TileNavigator], so moving off the left/right edge switches page. On touch screens a
 * horizontal swipe switches page and a tap opens the camera.
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
    val tileRequesters = remember(tiles.size) { List(tiles.size) { FocusRequester() } }
    val settingsRequester = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current

    // Put focus on the current tile when the page or target changes (and on entering the grid).
    // Only in key mode: on a phone a focus border on a random tile would just be noise.
    LaunchedEffect(currentPage, currentIndex, gridPage.view) {
        if (inputModeManager.inputMode == InputMode.Keyboard && currentIndex >= 0) {
            tileRequesters[currentIndex].tryRequestFocus()
        }
    }

    val pageCount = pages.size
    val swipeToPage by rememberUpdatedState { delta: Int ->
        val target = currentPage + delta
        if (target in 0 until pageCount) onPositionChange(GridPosition(target, 0))
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
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val direction = event.key.toDirection() ?: return@onPreviewKeyEvent false
                    if (currentIndex < 0) return@onPreviewKeyEvent false
                    val from = GridPosition(currentPage, currentIndex)
                    val to = TileNavigator.move(pages, from, direction)
                    when {
                        to != from -> {
                            onPositionChange(to)
                            // Same page: move focus right away; a new page focuses via the effect above.
                            if (to.page == currentPage) tileRequesters[to.index].tryRequestFocus()
                        }
                        // Top edge: go up to the settings button in the corner.
                        direction == Direction.UP -> settingsRequester.tryRequestFocus()
                    }
                    true
                }
                .pointerInput(Unit) {
                    val threshold = 80.dp.toPx()
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            when {
                                total < -threshold -> swipeToPage(1)
                                total > threshold -> swipeToPage(-1)
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
            tiles.forEachIndexed { index, placed ->
                val tile = placed.tile
                Box(
                    Modifier
                        .offset(x = cellWidth * tile.x, y = cellHeight * tile.y)
                        .size(width = cellWidth * tile.w, height = cellHeight * tile.h)
                        .padding(2.dp),
                ) {
                    // A tile showing a different camera after a page switch restarts its player,
                    // because rememberLiveStream is keyed by the URL. A fixed tile that keeps its
                    // camera across pages keeps playing.
                    val camera = placed.camera
                    if (camera != null) {
                        CameraTile(
                            video = video,
                            camera = camera,
                            fit = tile.fit,
                            focusRequester = tileRequesters[index],
                            onFocused = { onPositionChange(GridPosition(currentPage, index)) },
                            onClick = { onOpenCamera(camera.id) },
                        )
                    }
                }
            }
        }

        if (pageCount > 1) {
            val viewName = gridPage.view.name
            val position = stringResource(Res.string.page_indicator, currentPage + 1, pageCount)
            // Top centre: tile names sit bottom-left in every tile, so a bottom indicator would
            // cover the name of whichever tile ends there.
            Text(
                text = if (viewName.isBlank()) position else stringResource(Res.string.page_indicator_named, viewName, position),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .focusBorder(width = 3.dp, shape = CircleShape)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .focusRequester(settingsRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        // Back into the grid, to the tile that had focus before.
                        Key.DirectionDown, Key.DirectionLeft -> {
                            if (currentIndex >= 0) tileRequesters[currentIndex].tryRequestFocus()
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

@Composable
private fun CameraTile(
    video: VideoPlatform,
    camera: Camera,
    fit: FitMode,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val stream = video.rememberLiveStream(camera.gridUrl, camera.streamType, camera.name, audioEnabled = false)
    Box(
        Modifier
            .fillMaxSize()
            .focusBorder(width = 5.dp, shape = RoundedCornerShape(4.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(onClick = onClick)
            .background(Color.Black),
    ) {
        video.Surface(stream, Modifier.fillMaxSize(), fit)
        StreamStatusBadge(
            status = stream?.status ?: StreamStatus.Connecting,
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
