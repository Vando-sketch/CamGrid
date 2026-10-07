package io.github.vandosketch.camgrid.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.platform.VideoZoom
import io.github.vandosketch.camgrid.platform.zoomed

/**
 * Renders [player] centred in [modifier]'s bounds: letterboxed to the video's aspect ratio for
 * [FitMode.FIT], filling the bounds with the overflow cut off for [FitMode.CROP]. Shows black
 * until the first frame arrives (and while [player] is null). With a [zoom] the video is laid
 * out that much larger, moved and clipped to the bounds.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player?,
    modifier: Modifier = Modifier,
    fit: FitMode = FitMode.FIT,
    zoom: VideoZoom = VideoZoom.None,
) {
    // ContentFrame resizes its surface within the incoming max constraints, so it must not get
    // fixed (fillMaxSize) constraints itself; the inner Box provides loose ones.
    Box(modifier.clipToBounds()) {
        Box(Modifier.zoomed(zoom, MAX_ZOOMED_SURFACE_SIDE).fillMaxSize(), contentAlignment = Alignment.Center) {
            ContentFrame(
                player = player,
                // Cropping and zooming make the surface larger than its bounds. A SurfaceView is
                // composited outside the view hierarchy and would not be clipped, a TextureView
                // is drawn by Compose and is. Unzoomed fullscreen keeps the cheaper SurfaceView;
                // zooming in hands ExoPlayer the new surface (a moment of black at most).
                surfaceType = if (fit == FitMode.CROP || zoom.isZoomed) SURFACE_TYPE_TEXTURE_VIEW else SURFACE_TYPE_SURFACE_VIEW,
                contentScale = if (fit == FitMode.CROP) ContentScale.Crop else ContentScale.Fit,
            )
        }
    }
}

/**
 * The longest side, in pixels, a zoomed TextureView is laid out at: its buffers stay within
 * GPU texture limits and a Fire TV's memory (2560 x 1440 is about 15 MB per buffer). Zooming
 * further scales the drawn texture up. 2560 still shows a 1440p camera's own pixels at the
 * first zoom step on a 1080p TV.
 */
internal const val MAX_ZOOMED_SURFACE_SIDE = 2560
