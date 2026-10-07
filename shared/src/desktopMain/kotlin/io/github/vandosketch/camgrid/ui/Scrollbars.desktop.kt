package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal actual fun VerticalScrollbarFor(state: ScrollState, modifier: Modifier) {
    val adapter = rememberScrollbarAdapter(state)
    // maxValue is Int.MAX_VALUE until the content is measured.
    if (state.maxValue > 0 && state.maxValue != Int.MAX_VALUE) {
        VerticalScrollbar(adapter = adapter, modifier = modifier.testTag(SCROLLBAR_TAG), style = themedScrollbarStyle())
    }
}

@Composable
internal actual fun VerticalScrollbarFor(state: LazyListState, modifier: Modifier) {
    val adapter = rememberScrollbarAdapter(state)
    if (state.canScrollForward || state.canScrollBackward) {
        VerticalScrollbar(adapter = adapter, modifier = modifier.testTag(SCROLLBAR_TAG), style = themedScrollbarStyle())
    }
}

/** The default style draws a black thumb, which disappears on the dark theme. */
@Composable
private fun themedScrollbarStyle(): ScrollbarStyle {
    val thumb = MaterialTheme.colorScheme.onSurface
    return ScrollbarStyle(
        minimalHeight = 24.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        hoverDurationMillis = 300,
        unhoverColor = thumb.copy(alpha = 0.35f),
        hoverColor = thumb.copy(alpha = 0.6f),
    )
}
