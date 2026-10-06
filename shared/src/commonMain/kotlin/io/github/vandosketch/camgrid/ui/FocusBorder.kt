package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** High-contrast focus colour, visible over video and over the dark theme. */
val FocusColor = Color(0xFFFFC107)

/**
 * Draws a thick border while this element or one of its children has focus, so the D-pad
 * position is always obvious from across the room. Place it before the focusable modifier
 * (clickable, toggleable, ...) or on a component whose content is focusable.
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
) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {

    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.hasFocus != focused) {
            focused = focusState.hasFocus
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused) {
            val strokePx = width.toPx()
            // Inset by half the stroke so the whole border is drawn inside the bounds.
            inset(strokePx / 2f) {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = color,
                    style = Stroke(width = strokePx),
                )
            }
        }
    }
}
