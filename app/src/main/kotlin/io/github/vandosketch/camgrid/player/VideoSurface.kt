package io.github.vandosketch.camgrid.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
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

/**
 * Renders [player] centred in [modifier]'s bounds: letterboxed to the video's aspect ratio for
 * [FitMode.FIT], filling the bounds with the overflow cut off for [FitMode.CROP]. Shows black
 * until the first frame arrives (and while [player] is null).
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier, fit: FitMode = FitMode.FIT) {
    // ContentFrame resizes its surface within the incoming max constraints, so it must not get
    // fixed (fillMaxSize) constraints itself; the Box provides loose ones.
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        ContentFrame(
            player = player,
            // Cropping makes the surface larger than the tile. A SurfaceView is composited
            // outside the view hierarchy and would not be clipped, a TextureView is.
            surfaceType = if (fit == FitMode.CROP) SURFACE_TYPE_TEXTURE_VIEW else SURFACE_TYPE_SURFACE_VIEW,
            contentScale = if (fit == FitMode.CROP) ContentScale.Crop else ContentScale.Fit,
        )
    }
}
