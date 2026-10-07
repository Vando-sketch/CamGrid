package io.github.vandosketch.camgrid.platform

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoZoomTest {

    private fun assertNear(expected: Float, actual: Float, message: String = "") {
        assertTrue(abs(expected - actual) < 0.0001f, "$message expected $expected, was $actual")
    }

    @Test
    fun anUnknownFocusPointZoomsAroundTheCentre() {
        // A gesture event can report an unspecified (NaN) centroid; it must not poison the zoom.
        val zoom = VideoZoom.None.zoomBy(2f, Float.NaN, Float.NaN)
        assertEquals(2f, zoom.scale)
        assertEquals(0f, zoom.offsetX)
        assertEquals(0f, zoom.offsetY)
        val panned = zoom.panBy(Float.NaN, 0.1f)
        assertEquals(0f, panned.offsetX)
        assertNear(0.1f, panned.offsetY)
    }

    @Test
    fun zoomingPastTheMaximumKeepsTheFocusPointStill() {
        // Pinching past 4x must not move the picture as if it had zoomed further.
        val atMax = VideoZoom.None.zoomBy(4f, 0.6f, 0.5f)
        assertEquals(atMax, atMax.zoomBy(2f, 0.6f, 0.5f))
    }

    @Test
    fun noneShowsTheWholePicture() {
        val none = VideoZoom.None
        assertEquals(1f, none.scale)
        assertEquals(0f, none.offsetX)
        assertEquals(0f, none.offsetY)
        assertFalse(none.isZoomed)
    }

    @Test
    fun zoomingAroundTheCentreKeepsThePictureCentred() {
        val zoom = VideoZoom.None.zoomBy(2f)
        assertTrue(zoom.isZoomed)
        assertEquals(2f, zoom.scale)
        assertEquals(0f, zoom.offsetX)
        assertEquals(0f, zoom.offsetY)
    }

    @Test
    fun scaleIsClampedToOneToFour() {
        var zoom = VideoZoom.None
        repeat(10) { zoom = zoom.zoomBy(VideoZoom.STEP) }
        assertEquals(VideoZoom.MAX_SCALE, zoom.scale)
        assertEquals(VideoZoom.None, VideoZoom.None.zoomBy(0.5f))
        assertEquals(VideoZoom.None, VideoZoom.None.zoomBy(10f).zoomBy(0.01f))
    }

    @Test
    fun zoomingOutAllTheWaySnapsBackToNone() {
        var zoom = VideoZoom.None.zoomBy(VideoZoom.STEP, focusX = 0.9f, focusY = 0.1f)
        assertTrue(zoom.isZoomed)
        zoom = zoom.zoomBy(1f / VideoZoom.STEP)
        assertEquals(VideoZoom.None, zoom)
        // Almost 1 counts as 1, so a pinch that ends near the start leaves no tiny zoom behind.
        assertEquals(VideoZoom.None, VideoZoom.None.zoomBy(1.005f))
    }

    @Test
    fun theFocusPointStaysUnderTheFingers() {
        // Pinching at a quarter of the width: that point of the picture stays where it was.
        val zoom = VideoZoom.None.zoomBy(2f, focusX = 0.25f, focusY = 0.5f)
        assertNear(0.25f, zoom.offsetX)
        assertNear(0f, zoom.offsetY)
        // The picture point at 0.25 (centre-relative -0.25) is drawn at centre + offset + p * scale.
        assertNear(-0.25f, zoom.offsetX + -0.25f * zoom.scale)
    }

    @Test
    fun zoomingIntoACornerKeepsThatCornerInPlace() {
        val zoom = VideoZoom.None.zoomBy(2f, focusX = 1f, focusY = 1f)
        // The picture, twice the size, moved up and left by half the screen: its bottom right
        // corner is still the screen's.
        assertNear(-0.5f, zoom.offsetX)
        assertNear(-0.5f, zoom.offsetY)
    }

    @Test
    fun panningIsClampedSoThePictureNeverLeavesTheScreen() {
        val zoom = VideoZoom.None.zoomBy(2f)
        // At 2x the picture is one screen wider than the screen: half a screen each way.
        val panned = zoom.panBy(3f, -3f)
        assertNear(0.5f, panned.offsetX)
        assertNear(-0.5f, panned.offsetY)
        assertNear(0.1f, zoom.panBy(0.1f, 0f).offsetX)
    }

    @Test
    fun panningTheWholePictureDoesNothing() {
        assertEquals(VideoZoom.None, VideoZoom.None.panBy(0.3f, -0.2f))
    }

    @Test
    fun zoomingOutPullsThePictureBackOntoTheScreen() {
        val zoomedIntoTheEdge = VideoZoom.None.zoomBy(VideoZoom.MAX_SCALE, focusX = 0f, focusY = 0.5f)
        assertNear(1.5f, zoomedIntoTheEdge.offsetX)
        val out = zoomedIntoTheEdge.zoomBy(0.5f)
        assertEquals(2f, out.scale)
        assertTrue(out.offsetX <= (out.scale - 1f) / 2f + 0.0001f, "offset ${out.offsetX} beyond the edge")
        assertNear(0.5f, out.offsetX)
    }

    @Test
    fun scaleTextHasOneDecimal() {
        assertEquals("1.0", VideoZoom.None.scaleText())
        assertEquals("1.5", VideoZoom.None.zoomBy(1.5f).scaleText())
        assertEquals("2.3", VideoZoom.None.zoomBy(2.25f).scaleText())
        assertEquals("4.0", VideoZoom.None.zoomBy(9f).scaleText())
    }

    @Test
    fun layoutScaleStopsAtTheCap() {
        // 1920 px wide, at most 2560 laid out: 4/3 of the zoom in layout, the rest as a layer.
        assertNear(2560f / 1920f, zoomLayoutScale(4f, 1920, 2560))
        assertEquals(1.2f, zoomLayoutScale(1.2f, 1920, 2560))
        assertEquals(3f, zoomLayoutScale(3f, 1920, Int.MAX_VALUE))
        // Content already larger than the cap is never laid out smaller than unzoomed.
        assertEquals(1f, zoomLayoutScale(2f, 4000, 2560))
    }
}
