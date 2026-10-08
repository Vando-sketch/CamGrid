package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** High-contrast focus colour, visible over video and over the dark theme. */
val FocusColor = Color(0xFFFFC107)

/**
 * How much of the focus rings to draw, 0 (none) to 1, read while drawing so an animation only
 * redraws them. The grid fades its rings out while nobody presses a key ([GridScreen]); everywhere
 * else it is 1.
 */
val LocalFocusRingAlpha = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * Draws a thick border while this element or one of its children has focus, so the D-pad
 * position is always obvious from across the room. Place it before the focusable modifier
 * (clickable, toggleable, ...) or on a component whose content is focusable.
 *
 * With a keyboard and mouse ([LocalKeyboardNavigation]) the border shows only while the user
 * moves around with the keys, so a button clicked with the mouse (which takes focus) is not
 * left ringed. Without one (TV, phone) it shows whenever there is focus. [LocalFocusRingAlpha]
 * fades it.
 */
fun Modifier.focusBorder(
    width: Dp = 3.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    color: Color = FocusColor,
): Modifier = this then FocusBorderElement(width, shape, color)

/**
 * Waits one frame, then requests focus. For effects that run right after composition, when
 * the target may still be waiting to be laid out (lazy list items, dialog content).
 */
suspend fun FocusRequester.requestFocusAfterLayout() {
    withFrameNanos { }
    tryRequestFocus()
}

/** Requests focus, ignoring the failure when the requester is not attached (yet). */
fun FocusRequester.tryRequestFocus() {
    try {
        requestFocus()
    } catch (e: IllegalStateException) {
        // Not attached to a focusable node; nothing to focus.
    }
}

private data class FocusBorderElement(
    val width: Dp,
    val shape: Shape,
    val color: Color,
) : ModifierNodeElement<FocusBorderNode>() {
    override fun create() = FocusBorderNode(width, shape, color)

    override fun update(node: FocusBorderNode) {
        node.width = width
        node.shape = shape
        node.color = color
        node.invalidateDraw()
    }
}

private class FocusBorderNode(
    var width: Dp,
    var shape: Shape,
    var color: Color,
) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode, CompositionLocalConsumerModifierNode {

    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.hasFocus != focused) {
            focused = focusState.hasFocus
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        // State reads: the border redraws when the user switches between keys and mouse, and
        // while it fades.
        val alpha = if (focused && currentValueOf(LocalKeyboardNavigation)?.active != false) {
            currentValueOf(LocalFocusRingAlpha)()
        } else {
            0f
        }
        if (alpha > 0f) {
            val strokePx = width.toPx()
            // Inset by half the stroke so the whole border is drawn inside the bounds.
            inset(strokePx / 2f) {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = color,
                    alpha = alpha,
                    style = Stroke(width = strokePx),
                )
            }
        }
    }
}
