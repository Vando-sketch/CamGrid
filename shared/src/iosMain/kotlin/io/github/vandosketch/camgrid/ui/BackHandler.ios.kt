package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
internal actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}
