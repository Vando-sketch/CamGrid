package io.github.vandosketch.camgrid.desktop.video

import io.github.vandosketch.camgrid.core.FitMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Which part of a frame (whole pixels) is drawn where in the box (box coordinates). */
data class DrawRects(
    val srcLeft: Int,
    val srcTop: Int,
    val srcWidth: Int,
    val srcHeight: Int,
    val dstLeft: Float,
    val dstTop: Float,
    val dstWidth: Float,
    val dstHeight: Float,
)

/** Scaling of video frames into tiles, the same as Android's SCALE_ASPECT_FIT / _FILL. */
object FrameGeometry {

    /**
     * Where a [frameWidth] x [frameHeight] frame is drawn in a [boxWidth] x [boxHeight] box:
     * [FitMode.FIT] shows the whole frame centred with bars, [FitMode.CROP] fills the box and
     * cuts the centred overflow off the source. Null when the frame or the box is empty.
     */
    fun place(frameWidth: Int, frameHeight: Int, boxWidth: Float, boxHeight: Float, fit: FitMode): DrawRects? {
        if (frameWidth <= 0 || frameHeight <= 0 || boxWidth <= 0f || boxHeight <= 0f) return null
        val scaleX = boxWidth / frameWidth
        val scaleY = boxHeight / frameHeight
        return when (fit) {
            FitMode.FIT -> {
                val scale = min(scaleX, scaleY)
                val w = frameWidth * scale
                val h = frameHeight * scale
                DrawRects(0, 0, frameWidth, frameHeight, (boxWidth - w) / 2, (boxHeight - h) / 2, w, h)
            }
            FitMode.CROP -> {
                val scale = max(scaleX, scaleY)
                val srcW = (boxWidth / scale).roundToInt().coerceIn(1, frameWidth)
                val srcH = (boxHeight / scale).roundToInt().coerceIn(1, frameHeight)
                DrawRects((frameWidth - srcW) / 2, (frameHeight - srcH) / 2, srcW, srcH, 0f, 0f, boxWidth, boxHeight)
            }
        }
    }

    /**
     * The size to convert a decoded frame to before it is drawn in a [boxWidth] x [boxHeight]
     * pixel box: no bigger than what is visible, never upscaled, even in both dimensions
     * (chroma planes are half size) and at least 2x2. A box of 0 (not laid out yet) keeps the
     * frame size.
     */
    fun decodeSize(frameWidth: Int, frameHeight: Int, boxWidth: Int, boxHeight: Int, fit: FitMode): Pair<Int, Int> {
        val scale = if (boxWidth <= 0 || boxHeight <= 0) {
            1.0
        } else {
            val sx = boxWidth.toDouble() / frameWidth
            val sy = boxHeight.toDouble() / frameHeight
            min(1.0, if (fit == FitMode.FIT) min(sx, sy) else max(sx, sy))
        }
        return even(frameWidth * scale) to even(frameHeight * scale)
    }

    private fun even(value: Double): Int = max(2, (value.roundToInt() / 2) * 2)
}
