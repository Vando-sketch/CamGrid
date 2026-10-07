package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.close
import io.github.vandosketch.camgrid.shared.resources.fullscreen_hint
import io.github.vandosketch.camgrid.shared.resources.fullscreen_hint_keys
import io.github.vandosketch.camgrid.shared.resources.fullscreen_position
import io.github.vandosketch.camgrid.shared.resources.sound_off
import io.github.vandosketch.camgrid.shared.resources.sound_on
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.VideoPlatform
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 4_000L

/**
 * One camera fullscreen, high-res stream with sound (unless muted).
 *
 * D-pad LEFT/RIGHT (or a swipe) switch to the previous/next camera, 1-9 jump to that camera,
 * OK (or M, Space) toggles the sound, other arrows just show the overlay. A tap toggles the
 * overlay, moving the mouse shows it, and its close button calls [onClose]. Back (and Esc) are
 * handled by the caller.
 */
@Composable
fun FullscreenScreen(
    video: VideoPlatform,
    cameras: List<Camera>,
    cameraId: String,
    onSwitchCamera: (String) -> Unit,
    onClose: () -> Unit,
) {
    val index = cameras.indexOfFirst { it.id == cameraId }
    if (index < 0) {
        // The camera was deleted (e.g. via settings); nothing to show.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val camera = cameras[index]

    var muted by rememberSaveable { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }
    // Bumped on every interaction; restarting the effect below restarts the hide timer.
    var overlayTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(overlayTrigger) {
        delay(OVERLAY_TIMEOUT_MS)
        overlayVisible = false
    }
    val showOverlay: () -> Unit = {
        overlayVisible = true
        overlayTrigger++
    }
    val switchBy: (Int) -> Unit = { delta ->
        if (cameras.size > 1) {
            onSwitchCamera(cameras[(index + delta).mod(cameras.size)].id)
            showOverlay()
        }
    }

    val stream = video.rememberLiveStream(camera.fullscreenUrl, camera.streamType, camera.name, audioEnabled = true)
    SideEffect { stream?.setMuted(muted) }

    // The root box takes focus so it receives the D-pad keys.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .keepScreenOn()
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val digit = event.key.digit()
                if (digit != null) {
                    cameras.getOrNull(digit - 1)?.let {
                        onSwitchCamera(it.id)
                        showOverlay()
                    }
                    return@onKeyEvent true
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        switchBy(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        switchBy(1)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.M, Key.Spacebar -> {
                        // A held key toggles once, not on and off at the repeat rate.
                        if (!event.isRepeat) {
                            muted = !muted
                            showOverlay()
                        }
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        showOverlay()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(index, cameras.size) {
                val threshold = 80.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total < -threshold -> switchBy(1)
                            total > threshold -> switchBy(-1)
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    total += dragAmount
                }
            }
            .pointerInput(Unit) {
                // A moving mouse shows the overlay (and its close button), like video players.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Move && event.changes.any { it.type == PointerType.Mouse }) {
                            showOverlay()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (overlayVisible) {
                            overlayVisible = false
                        } else {
                            showOverlay()
                        }
                    },
                )
            },
    ) {
        video.Surface(stream, Modifier.fillMaxSize(), FitMode.FIT)
        StreamStatusBadge(
            status = stream?.status ?: StreamStatus.Connecting,
            modifier = Modifier.align(Alignment.Center),
        )
        if (overlayVisible) {
            FullscreenOverlay(
                camera = camera,
                position = index + 1,
                count = cameras.size,
                muted = muted,
                onToggleMute = {
                    muted = !muted
                    showOverlay()
                },
                onClose = onClose,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FullscreenOverlay(
    camera: Camera,
    position: Int,
    count: Int,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrim = Color.Black.copy(alpha = 0.6f)
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scrim)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = camera.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.fullscreen_position, position, count),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.weight(1f))
            // For touch only: on the D-pad, OK toggles the sound, so this never takes focus.
            TextButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(
                    text = stringResource(if (muted) Res.string.sound_off else Res.string.sound_on),
                    color = Color.White,
                )
            }
            // Touch and mouse only, like the sound button; Back and Esc do this from the keys.
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.close),
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            // A keyboard has no OK key and no Back key: tell desktop users the keys they have.
            text = stringResource(if (LocalHasKeyboardAndMouse.current) Res.string.fullscreen_hint_keys else Res.string.fullscreen_hint),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(12.dp)
                .background(scrim, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
