package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Test tag of the desktop scrollbars that [VerticalScrollbarFor] draws. */
internal const val SCROLLBAR_TAG = "scrollbar"

/**
 * A vertical scrollbar for content scrolled by [state], shown while there is something to
 * scroll. Only the desktop app draws one: there a mouse without a wheel (or a window too short
 * to show a whole screen) otherwise gives no hint that more is below and no way to get there.
 * Touch screens and the TV remote scroll without it, so Android and iOS draw nothing.
 */
@Composable
internal expect fun VerticalScrollbarFor(state: ScrollState, modifier: Modifier = Modifier)

/** [VerticalScrollbarFor] for a lazy list. */
@Composable
internal expect fun VerticalScrollbarFor(state: LazyListState, modifier: Modifier = Modifier)

/**
 * [content] (which scrolls with [state]) with the scrollbar over its end edge. The scrollbar
 * sits next to the content, not around it, so the mouse wheel still scrolls the content.
 */
@Composable
internal fun ScrollbarBox(state: ScrollState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        content()
        VerticalScrollbarFor(state, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** [ScrollbarBox] for a lazy list. */
@Composable
internal fun ScrollbarBox(state: LazyListState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        content()
        VerticalScrollbarFor(state, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
