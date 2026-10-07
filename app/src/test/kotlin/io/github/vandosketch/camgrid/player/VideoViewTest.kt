package io.github.vandosketch.camgrid.player

import io.github.vandosketch.camgrid.core.FitMode
import io.github.vandosketch.camgrid.platform.VideoZoom
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which Android view draws a stream: fullscreen never swaps it while zooming (issue #30). */
class VideoViewTest {

    @Test
    fun fullscreenKeepsOneViewFromTheWholePictureToZoomedIn() {
        val zoomedIn = VideoZoom.None.zoomBy(2f)
        assertEquals(VideoView.TEXTURE_VIEW, videoViewFor(FitMode.FIT, VideoZoom.None))
        assertEquals(VideoView.TEXTURE_VIEW, videoViewFor(FitMode.FIT, zoomedIn))
    }

    @Test
    fun gridTilesKeepTheCheaperSurfaceView() {
        assertEquals(VideoView.SURFACE_VIEW, videoViewFor(FitMode.FIT, zoom = null))
    }

    @Test
    fun croppedTilesUseATextureViewThatCanBeClipped() {
        assertEquals(VideoView.TEXTURE_VIEW, videoViewFor(FitMode.CROP, zoom = null))
    }
}
