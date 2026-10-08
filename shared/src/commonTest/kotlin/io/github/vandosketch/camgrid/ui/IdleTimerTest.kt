package io.github.vandosketch.camgrid.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class IdleTimerTest {

    @Test
    fun goesIdleAfterTheTimeout() = runTest {
        val timer = IdleTimer(timeoutMillis = 1_000)
        var idleCalls = 0
        val job = launch { timer.run { idleCalls++ } }
        runCurrent()
        assertFalse(timer.idle)
        advanceTimeBy(999)
        assertFalse(timer.idle)
        advanceTimeBy(2)
        assertTrue(timer.idle)
        assertEquals(1, idleCalls)
        job.cancel()
    }

    @Test
    fun activityWakesItAndRestartsTheTimeout() = runTest {
        val timer = IdleTimer(timeoutMillis = 1_000)
        var idleCalls = 0
        val job = launch { timer.run { idleCalls++ } }
        advanceTimeBy(800)
        timer.onActivity()
        // 800 ms after the activity: not 1 s since the start counts, but since the last input.
        advanceTimeBy(800)
        assertFalse(timer.idle)
        advanceTimeBy(201)
        assertTrue(timer.idle)
        timer.onActivity()
        assertFalse(timer.idle, "activity wakes it at once, not on the next frame")
        advanceTimeBy(1_001)
        assertTrue(timer.idle)
        assertEquals(2, idleCalls)
        job.cancel()
    }

    @Test
    fun staysIdleWithoutActivity() = runTest {
        val timer = IdleTimer(timeoutMillis = 1_000)
        var idleCalls = 0
        val job = launch { timer.run { idleCalls++ } }
        advanceTimeBy(10_000)
        assertTrue(timer.idle)
        assertEquals(1, idleCalls)
        job.cancel()
    }
}
