package io.github.vandosketch.camgrid.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * How far fullscreen video is magnified (issue #22): [scale] times its bounds (1 = the whole
 * picture, at most [MAX_SCALE]) around their centre, then moved by [offsetX] and [offsetY],
 * fractions of the bounds' width and height. Every value is clamped so the magnified bounds
 * always cover the whole surface: the picture never leaves the screen. Immutable; the
 * functions return a new zoom. Positions and distances are fractions of the bounds, so a zoom
 * survives a change of size (rotation, window resize).
 */
class VideoZoom private constructor(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    /** Magnified; false for [None]. */
    val isZoomed: Boolean get() = scale > 1f

    /**
     * Magnifies by [factor] (below 1 zooms out) around the focus point, given as a fraction of
     * the bounds (0..1, the default is the centre): the picture under it stays in place, like
     * the spot between two pinching fingers.
     */
    fun zoomBy(factor: Float, focusX: Float = 0.5f, focusY: Float = 0.5f): VideoZoom {
        // Clamped first, so zooming past the limits never moves the picture.
        val newScale = (scale * factor.finiteOr(1f)).coerceIn(1f, MAX_SCALE)
        // The focus point relative to the centre (the centre when unknown, NaN), and where in
        // the unmagnified picture it is.
        val fx = focusX.finiteOr(0.5f) - 0.5f
        val fy = focusY.finiteOr(0.5f) - 0.5f
        val pictureX = (fx - offsetX) / scale
        val pictureY = (fy - offsetY) / scale
        return of(newScale, fx - pictureX * newScale, fy - pictureY * newScale)
    }

    /** Moves the picture by [dx] and [dy], fractions of the bounds' width and height. */
    fun panBy(dx: Float, dy: Float): VideoZoom = of(scale, offsetX + dx.finiteOr(0f), offsetY + dy.finiteOr(0f))

    /** The scale with one decimal, "2.5", for the fullscreen hint. */
    fun scaleText(): String {
        val tenths = (scale * 10f).roundToInt()
        return "${tenths / 10}.${tenths % 10}"
    }

    override fun equals(other: Any?): Boolean =
        other is VideoZoom && other.scale == scale && other.offsetX == offsetX && other.offsetY == offsetY

    override fun hashCode(): Int = (scale.hashCode() * 31 + offsetX.hashCode()) * 31 + offsetY.hashCode()

    override fun toString(): String = "VideoZoom(scale=$scale, offsetX=$offsetX, offsetY=$offsetY)"

    companion object {
        /** The whole picture. */
        val None = VideoZoom(1f, 0f, 0f)

        const val MAX_SCALE = 4f

        /** One zoom key press (Fast-forward, +): 1, 1.5, 2.25, 3.4, 4. */
        const val STEP = 1.5f

        // Below this a zoom snaps back to the whole picture, so no barely visible zoom is left.
        private const val SNAP_TO_NONE = 1.01f

        private fun of(scale: Float, offsetX: Float, offsetY: Float): VideoZoom {
            val clampedScale = scale.coerceIn(1f, MAX_SCALE)
            if (clampedScale < SNAP_TO_NONE) return None
            // Each edge of the magnified picture may move in by (scale - 1) / 2 bounds at most.
            val limit = (clampedScale - 1f) / 2f
            // + 0f turns -0 into 0, so equal zooms have equal hash codes.
            return VideoZoom(clampedScale, offsetX.coerceIn(-limit, limit) + 0f, offsetY.coerceIn(-limit, limit) + 0f)
        }
    }
}

/**
 * Shows the content [VideoZoom.scale] times the incoming maximum size, moved per [zoom]; the
 * caller clips to the bounds. The content is laid out larger (so a platform's video renders the
 * magnified picture at that resolution rather than upscaling it), but with its longer side at
 * most [maxLayoutSide] pixels: native video buffers must stay within GPU texture limits and
 * memory. The rest of the magnification is a graphics layer scale, which only content drawn by
 * Compose follows (a Compose canvas, an Android TextureView; not a SurfaceView or UIKit view).
 * Without a zoom (or without bounded constraints) it does nothing; the modifier chain is the
 * same either way, so zooming never recreates a video view.
 */
fun Modifier.zoomed(zoom: VideoZoom, maxLayoutSide: Int = Int.MAX_VALUE): Modifier = layout { measurable, constraints ->
    if (!zoom.isZoomed || !constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val layoutScale = zoomLayoutScale(zoom.scale, maxOf(width, height), maxLayoutSide)
    val layerScale = zoom.scale / layoutScale
    val placeable = measurable.measure(
        Constraints.fixed((width * layoutScale).roundToInt(), (height * layoutScale).roundToInt()),
    )
    // The centre of the magnified picture, which the layer scales around.
    val centreX = width / 2f + zoom.offsetX * width
    val centreY = height / 2f + zoom.offsetY * height
    layout(width, height) {
        val x = (centreX - placeable.width / 2f).roundToInt()
        val y = (centreY - placeable.height / 2f).roundToInt()
        if (layerScale > 1f) {
            placeable.placeWithLayer(x, y) {
                scaleX = layerScale
                scaleY = layerScale
            }
        } else {
            placeable.place(x, y)
        }
    }
}

/**
 * How much of [scale] a [zoomed] content of [side] pixels (its longer side) is laid out larger:
 * all of it, up to [maxLayoutSide] pixels, never below 1.
 */
internal fun zoomLayoutScale(scale: Float, side: Int, maxLayoutSide: Int): Float {
    if (side <= 0) return scale
    return minOf(scale, maxLayoutSide.toFloat() / side).coerceAtLeast(1f)
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
