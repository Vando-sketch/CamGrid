package io.github.vandosketch.camgrid.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Touch and the TV remote scroll without a scrollbar.
@Composable
internal actual fun VerticalScrollbarFor(state: ScrollState, modifier: Modifier) {
}

@Composable
internal actual fun VerticalScrollbarFor(state: LazyListState, modifier: Modifier) {
}
