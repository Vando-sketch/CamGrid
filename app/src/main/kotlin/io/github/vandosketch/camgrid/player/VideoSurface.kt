package io.github.vandosketch.camgrid.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW

/**
 * Renders [player] on a SurfaceView, letterboxed to the video's aspect ratio and centred in
 * [modifier]'s bounds. Shows black until the first frame arrives (and while [player] is null).
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier) {
    // ContentFrame resizes its surface within the incoming max constraints, so it must not get
    // fixed (fillMaxSize) constraints itself; the Box provides loose ones.
    Box(modifier, contentAlignment = Alignment.Center) {
        ContentFrame(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            contentScale = ContentScale.Fit,
        )
    }
}
