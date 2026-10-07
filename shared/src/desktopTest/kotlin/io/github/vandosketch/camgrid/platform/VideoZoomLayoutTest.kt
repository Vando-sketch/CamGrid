package io.github.vandosketch.camgrid.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** [zoomed] lays the video out larger and shifted, as the platforms' surfaces use it. */
@OptIn(ExperimentalTestApi::class)
class VideoZoomLayoutTest {

    private fun assertNear(expected: Dp, actual: Dp, what: String) {
        assertTrue(abs(expected.value - actual.value) <= 1f, "$what: expected $expected, was $actual")
    }

    @Test
    fun zoomedVideoIsLaidOutLargerAndShifted() = runComposeUiTest {
        var zoom by mutableStateOf(VideoZoom.None)
        setContent {
            Box(Modifier.size(200.dp).clipToBounds().testTag("bounds")) {
                Box(Modifier.zoomed(zoom).fillMaxSize().testTag("video"))
            }
        }
        val bounds = onNodeWithTag("bounds").getUnclippedBoundsInRoot()
        var video = onNodeWithTag("video").getUnclippedBoundsInRoot()
        assertNear(bounds.left, video.left, "left at 1x")
        assertNear(bounds.right, video.right, "right at 1x")

        // Twice the size, zoomed into the bottom right corner, which stays where it was.
        zoom = VideoZoom.None.zoomBy(2f, focusX = 1f, focusY = 1f)
        waitForIdle()
        video = onNodeWithTag("video").getUnclippedBoundsInRoot()
        assertNear(bounds.left - 200.dp, video.left, "left at 2x")
        assertNear(bounds.top - 200.dp, video.top, "top at 2x")
        assertNear(bounds.right, video.right, "right at 2x")
        assertNear(bounds.bottom, video.bottom, "bottom at 2x")

        // Centred at 3x: a screen sticks out on each side.
        zoom = VideoZoom.None.zoomBy(3f)
        waitForIdle()
        video = onNodeWithTag("video").getUnclippedBoundsInRoot()
        assertNear(bounds.left - 200.dp, video.left, "left at 3x")
        assertNear(bounds.right + 200.dp, video.right, "right at 3x")
    }

    @Test
    fun beyondTheLayoutCapTheRestIsALayerScale() = runComposeUiTest {
        var measured = IntSize.Zero
        var capPx = 0
        setContent {
            capPx = with(LocalDensity.current) { 300.dp.roundToPx() }
            Box(Modifier.size(200.dp).clipToBounds().testTag("bounds")) {
                Box(
                    Modifier
                        .zoomed(VideoZoom.None.zoomBy(3f), maxLayoutSide = capPx)
                        .fillMaxSize()
                        .onSizeChanged { measured = it }
                        .testTag("video"),
                )
            }
        }
        waitForIdle()
        // Laid out at the cap (1.5x), drawn twice that: 3x, centred.
        assertTrue(abs(measured.width - capPx) <= 1 && abs(measured.height - capPx) <= 1, "laid out at $measured, cap $capPx")
        val bounds = onNodeWithTag("bounds").getUnclippedBoundsInRoot()
        val video = onNodeWithTag("video").getUnclippedBoundsInRoot()
        // The top left corner as drawn, after the layer's scale around the centre.
        assertNear(bounds.left - 200.dp, video.left, "left at 3x")
        assertNear(bounds.top - 200.dp, video.top, "top at 3x")
    }
}
