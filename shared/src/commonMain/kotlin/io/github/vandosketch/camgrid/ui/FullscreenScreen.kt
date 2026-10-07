package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.vandosketch.camgrid.core.Camera
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.platform.StreamStatus
import io.github.vandosketch.camgrid.platform.VideoPlatform
import io.github.vandosketch.camgrid.platform.VideoZoom
import io.github.vandosketch.camgrid.shared.resources.Res
import io.github.vandosketch.camgrid.shared.resources.close
import io.github.vandosketch.camgrid.shared.resources.fullscreen_hint
import io.github.vandosketch.camgrid.shared.resources.fullscreen_hint_keys
import io.github.vandosketch.camgrid.shared.resources.fullscreen_position
import io.github.vandosketch.camgrid.shared.resources.fullscreen_zoom_hint
import io.github.vandosketch.camgrid.shared.resources.fullscreen_zoom_hint_keys
import io.github.vandosketch.camgrid.shared.resources.fullscreen_zoomed_hint
import io.github.vandosketch.camgrid.shared.resources.fullscreen_zoomed_hint_keys
import io.github.vandosketch.camgrid.shared.resources.sound_mute
import io.github.vandosketch.camgrid.shared.resources.sound_unmute
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val OVERLAY_TIMEOUT_MS = 4_000L

/** One arrow key press moves a zoomed picture by this fraction of the screen. */
private const val PAN_STEP = 0.1f

/** A double tap zooms to this scale. */
private const val DOUBLE_TAP_SCALE = 2f

/** One mouse wheel notch zooms by this factor. */
private const val WHEEL_STEP = 1.2f

/** At most this many wheel notches count per event (a fast trackpad flick sends more). */
private const val MAX_WHEEL_NOTCHES = 3f

/**
 * One camera fullscreen, high-res stream with sound (unless muted).
 *
 * D-pad LEFT/RIGHT (or a swipe) switch to the previous/next camera, 1-9 jump to that camera,
 * OK (or M, Space) toggles the sound, other arrows just show the overlay. A tap toggles the
 * overlay, moving the mouse shows it, and its close button calls [onClose]. Back (and Esc) are
 * handled by the caller.
 *
 * Where the platform can magnify its video ([VideoPlatform.supportsZoom]), pinch, a double
 * tap, the mouse wheel, + and - (or Fast-forward and Rewind on a remote) zoom, and 0 shows the
 * whole picture again. While zoomed in, the arrows and dragging move the picture instead of switching camera,
 * and Back (Esc) first shows the whole picture again before it reaches the caller. Switching
 * camera resets the zoom.
 *
 * Until its own stream plays, it shows the camera's grid stream that [CamGridApp] keeps open
 * ([LocalGridStreams]), so opening a camera from the grid shows its picture at once instead of
 * black (issue #29).
 */
@OptIn(ExperimentalComposeUiApi::class) // scrollDelta (mouse wheel), experimental in some Compose versions
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

    val zoomable = video.supportsZoom
    // Another camera starts with the whole picture.
    var zoom by remember(camera.id) { mutableStateOf(VideoZoom.None) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Registered after the app's own Back handler, so it comes first while zoomed in.
    BackHandler(enabled = zoom.isZoomed) {
        zoom = VideoZoom.None
        showOverlay()
    }
    val zoomBy: (Float) -> Unit = { factor ->
        if (zoomable) {
            zoom = zoom.zoomBy(factor)
            showOverlay()
        }
    }

    val stream = video.rememberLiveStream(
        camera.fullscreenUrl, camera.streamType, camera.name, audioEnabled = true, lowerResolutionUrl = camera.gridUrl,
    )
    SideEffect { stream?.setMuted(muted) }
    val gridStream = LocalGridStreams.current[camera.id]
    // Over the stream while it connects; it keeps being drawn underneath, as a player may only
    // report playing once it has drawn a frame.
    val showGridStream = stream?.status != StreamStatus.Playing && gridStream?.status == StreamStatus.Playing

    // The root box takes focus so it receives the D-pad keys.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .keepScreenOn()
            .onSizeChanged { size = it }
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
                // Zoomed in, the arrows move the picture: Left shows more of its left side.
                val pan = if (zoom.isZoomed) event.key.panDirection() else null
                when {
                    event.isZoomIn() && zoomable -> {
                        zoomBy(VideoZoom.STEP)
                        true
                    }
                    event.isZoomOut() && zoomable -> {
                        zoomBy(1f / VideoZoom.STEP)
                        true
                    }
                    event.isZoomReset() && zoomable -> {
                        zoom = VideoZoom.None
                        showOverlay()
                        true
                    }
                    pan != null -> {
                        zoom = zoom.panBy(pan.first * PAN_STEP, pan.second * PAN_STEP)
                        true
                    }
                    else -> when (event.key) {
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
            }
            .focusable()
            .pointerInput(index, cameras.size, zoomable) {
                // One finger (or the mouse) swipes to the next camera; zoomed in, it moves the
                // picture instead. Two fingers pinch to zoom.
                val swipeThreshold = 80.dp.toPx()
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // A gesture that starts zoomed in, or ever has two fingers, never switches.
                    var swipe = !zoom.isZoomed
                    var swipeDistance = 0f
                    var pastSlop = false
                    var slopZoom = 1f
                    var slopPan = Offset.Zero
                    var cancelled = false
                    do {
                        val event = awaitPointerEvent()
                        cancelled = event.changes.any { it.isConsumed }
                        if (!cancelled) {
                            if (event.changes.count { it.pressed } > 1) swipe = false
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (!pastSlop) {
                                slopZoom *= zoomChange
                                slopPan += panChange
                                val zoomMotion = abs(1f - slopZoom) * event.calculateCentroidSize(useCurrent = false)
                                val slop = viewConfiguration.touchSlop
                                pastSlop = zoomMotion > slop || slopPan.getDistance() > slop
                            }
                            if (pastSlop) {
                                if (swipe) {
                                    swipeDistance += panChange.x
                                } else if (zoomable && size.width > 0 && size.height > 0) {
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    zoom = zoom
                                        .zoomBy(zoomChange, centroid.x / size.width, centroid.y / size.height)
                                        .panBy(panChange.x / size.width, panChange.y / size.height)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (!cancelled && event.changes.any { it.pressed })
                    if (swipe && !cancelled) {
                        when {
                            swipeDistance < -swipeThreshold -> switchBy(1)
                            swipeDistance > swipeThreshold -> switchBy(-1)
                        }
                    }
                }
            }
            .pointerInput(zoomable) {
                // The mouse wheel zooms around the pointer; a moving mouse shows the overlay
                // (and its close button), like video players.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when {
                            event.type == PointerEventType.Move && event.changes.any { it.type == PointerType.Mouse } ->
                                showOverlay()
                            event.type == PointerEventType.Scroll && zoomable && size.width > 0 && size.height > 0 -> {
                                val change = event.changes.firstOrNull() ?: continue
                                val notches = change.scrollDelta.y.coerceIn(-MAX_WHEEL_NOTCHES, MAX_WHEEL_NOTCHES)
                                if (notches != 0f) {
                                    // Wheel up (negative) zooms in.
                                    zoom = zoom.zoomBy(
                                        WHEEL_STEP.pow(-notches),
                                        change.position.x / size.width,
                                        change.position.y / size.height,
                                    )
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(zoomable) {
                detectTapGestures(
                    // Zooms into the tapped spot, or back to the whole picture. Only where zoom
                    // works: waiting for a second tap delays the single tap's overlay toggle.
                    onDoubleTap = if (zoomable) {
                        { position ->
                            zoom = if (zoom.isZoomed || size.width <= 0 || size.height <= 0) {
                                VideoZoom.None
                            } else {
                                VideoZoom.None.zoomBy(DOUBLE_TAP_SCALE, position.x / size.width, position.y / size.height)
                            }
                        }
                    } else {
                        null
                    },
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
        video.Surface(stream, Modifier.fillMaxSize(), FitMode.FIT, zoom)
        if (showGridStream) video.Surface(gridStream, Modifier.fillMaxSize(), FitMode.FIT, zoom)
        val status = stream?.status ?: StreamStatus.Connecting
        // A picture is showing while the stream connects; an offline stream still says so.
        if (!(showGridStream && status is StreamStatus.Connecting)) {
            StreamStatusBadge(status = status, modifier = Modifier.align(Alignment.Center))
        }
        if (overlayVisible) {
            FullscreenOverlay(
                camera = camera,
                position = index + 1,
                count = cameras.size,
                // Hidden only for a stream known to have no sound.
                hasAudio = stream?.hasAudio != false,
                muted = muted,
                zoom = zoom.takeIf { zoomable },
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

/** Fast-forward on a remote, + (or = , its unshifted key on US keyboards) on a keyboard. */
private fun KeyEvent.isZoomIn(): Boolean =
    key == Key.MediaFastForward || key == Key.Plus || key == Key.NumPadAdd || key == Key.Equals ||
        utf16CodePoint == '+'.code

/** Rewind on a remote, - on a keyboard. */
private fun KeyEvent.isZoomOut(): Boolean =
    key == Key.MediaRewind || key == Key.Minus || key == Key.NumPadSubtract || utf16CodePoint == '-'.code

private fun KeyEvent.isZoomReset(): Boolean = key == Key.Zero || key == Key.NumPad0

/** Which way an arrow moves a zoomed picture (x, y in -1..1), or null for other keys. */
private fun Key.panDirection(): Pair<Int, Int>? = when (this) {
    Key.DirectionLeft -> 1 to 0
    Key.DirectionRight -> -1 to 0
    Key.DirectionUp -> 0 to 1
    Key.DirectionDown -> 0 to -1
    else -> null
}

@Composable
private fun FullscreenOverlay(
    camera: Camera,
    position: Int,
    count: Int,
    hasAudio: Boolean,
    muted: Boolean,
    zoom: VideoZoom?,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrim = Color.Black.copy(alpha = 0.6f)
    val keyboard = LocalHasKeyboardAndMouse.current
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scrim)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Takes all the room the buttons leave, so they sit at the right edge; the name
            // only as wide as it needs, the position right after it.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
            }
            if (hasAudio) {
                // For touch and mouse only: on the D-pad, OK toggles the sound, so this never
                // takes focus. The icon shows the state, the description says what a press does.
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .focusProperties { canFocus = false }
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = if (muted) SoundIcons.Off else SoundIcons.On,
                        contentDescription = stringResource(if (muted) Res.string.sound_unmute else Res.string.sound_mute),
                        tint = Color.White,
                    )
                }
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
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (zoom != null && zoom.isZoomed) {
                val hint = if (keyboard) Res.string.fullscreen_zoomed_hint_keys else Res.string.fullscreen_zoomed_hint
                HintText(stringResource(hint, zoom.scaleText()), scrim)
            } else {
                // A keyboard has no OK key and no Back key: tell desktop users the keys they have.
                HintText(stringResource(if (keyboard) Res.string.fullscreen_hint_keys else Res.string.fullscreen_hint), scrim)
                if (zoom != null) {
                    HintText(
                        stringResource(if (keyboard) Res.string.fullscreen_zoom_hint_keys else Res.string.fullscreen_zoom_hint),
                        scrim,
                    )
                }
            }
        }
    }
}

@Composable
private fun HintText(text: String, scrim: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .background(scrim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
