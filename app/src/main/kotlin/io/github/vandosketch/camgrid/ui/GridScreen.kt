package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.R
import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.Direction
import io.github.vandosketch.camgrid.core.GridNavigator
import io.github.vandosketch.camgrid.core.GridPaging
import io.github.vandosketch.camgrid.core.GridPosition
import io.github.vandosketch.camgrid.player.StreamStatus
import io.github.vandosketch.camgrid.player.VideoSurface
import io.github.vandosketch.camgrid.player.rememberStreamPlayer

/**
 * The camera wall: `columns x rows` tiles per page, muted low-res streams.
 *
 * D-pad arrows are handled here (not by Compose's default focus search) with core's
 * [GridNavigator], so moving off the left/right edge switches page. On touch screens a
 * horizontal swipe switches page and a tap opens the camera.
 */
@Composable
fun GridScreen(
    config: CamGridConfig,
    page: Int,
    focusIndex: Int,
    onPositionChange: (GridPosition) -> Unit,
    onOpenCamera: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pages = remember(config) { GridPaging.pages(config) }
    if (pages.isEmpty()) {
        EmptyGrid(onOpenSettings)
        return
    }

    val layout = config.layout
    val currentPage = page.coerceIn(0, pages.lastIndex)
    val cameras = pages[currentPage]
    val currentIndex = focusIndex.coerceIn(0, cameras.lastIndex)
    val tileRequesters = remember(layout.tilesPerPage) { List(layout.tilesPerPage) { FocusRequester() } }
    val settingsRequester = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current

    // Put focus on the current tile when the page or target changes (and on entering the grid).
    // Only in key mode: on a phone a focus border on a random tile would just be noise.
    LaunchedEffect(currentPage, currentIndex, layout) {
        if (inputModeManager.inputMode == InputMode.Keyboard) {
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
        Column(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val direction = event.key.toDirection() ?: return@onPreviewKeyEvent false
                    val from = GridPosition(currentPage, currentIndex)
                    val to = GridNavigator.move(from, direction, layout, config.cameras.size)
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
            for (row in 0 until layout.rows) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    for (column in 0 until layout.columns) {
                        val index = row * layout.columns + column
                        val camera = cameras.getOrNull(index)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(2.dp),
                        ) {
                            // A slot showing a different camera after a page switch restarts its
                            // player, because rememberStreamPlayer is keyed by the URL.
                            if (camera != null) {
                                CameraTile(
                                    camera = camera,
                                    focusRequester = tileRequesters[index],
                                    onFocused = { onPositionChange(GridPosition(currentPage, index)) },
                                    onClick = { onOpenCamera(camera.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pageCount > 1) {
            Text(
                text = stringResource(R.string.page_indicator, currentPage + 1, pageCount),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                            tileRequesters[currentIndex].tryRequestFocus()
                            true
                        }
                        Key.DirectionUp, Key.DirectionRight -> true
                        else -> false
                    }
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.open_settings),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun CameraTile(
    camera: Camera,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val stream = rememberStreamPlayer(camera.gridUrl, camera.name, audioEnabled = false)
    Box(
        Modifier
            .fillMaxSize()
            .focusBorder(width = 5.dp, shape = RoundedCornerShape(4.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(onClick = onClick)
            .background(Color.Black),
    ) {
        VideoSurface(stream?.player, Modifier.fillMaxSize())
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
                    text = stringResource(R.string.status_offline, status.retryInSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                // Already redacted by StreamPlayer; it is an error code, not a URL.
                Text(
                    text = status.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            else -> Text(
                text = stringResource(R.string.status_connecting),
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
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_body),
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
            Text(stringResource(R.string.open_settings))
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
