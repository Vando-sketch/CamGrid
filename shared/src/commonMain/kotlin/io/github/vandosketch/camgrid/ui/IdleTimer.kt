package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Notices when nobody used the app for [timeoutMillis], like a video player hiding its controls:
 * the grid fades its selection ring, fullscreen goes back to the grid. Input reports itself with
 * [onActivity]; [run] does the counting while it is collected (in a LaunchedEffect, so a Compose
 * test's mainClock drives it, and the delay is the coroutine's, not the wall clock's).
 */
@Stable
class IdleTimer(private val timeoutMillis: Long) {
    /** Snapshot state: true from [timeoutMillis] after the last input until the next. */
    var idle by mutableStateOf(false)
        private set

    /** Bumped by each input; [run] restarts its countdown on every new value. */
    private val activity = MutableStateFlow(0L)

    /** Input happened: awake at once (a key handler may read [idle] right after), counting anew. */
    fun onActivity() {
        idle = false
        activity.value++
    }

    /** Counts down from the start and from each input; [onIdle] runs each time it goes idle. */
    suspend fun run(onIdle: () -> Unit = {}) {
        activity.collectLatest {
            delay(timeoutMillis)
            idle = true
            onIdle()
        }
    }
}
