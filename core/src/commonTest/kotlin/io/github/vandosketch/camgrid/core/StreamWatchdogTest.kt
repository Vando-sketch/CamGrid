package io.github.vandosketch.camgrid.core

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test

class StreamWatchdogTest {

    private val watchdog = StreamWatchdog(connectTimeoutMillis = 10_000, stallTimeoutMillis = 5_000)

    @Test
    fun defaults() {
        val defaults = StreamWatchdog()
        assertEquals(15_000L, defaults.connectTimeoutMillis)
        assertEquals(8_000L, defaults.stallTimeoutMillis)
    }

    @Test
    fun rejectsNonPositiveTimeouts() {
        assertFailsWith<IllegalArgumentException> { StreamWatchdog(connectTimeoutMillis = 0) }
        assertFailsWith<IllegalArgumentException> { StreamWatchdog(stallTimeoutMillis = -1) }
    }

    @Test
    fun waitingForFirstFrameWithinTimeout() {
        assertEquals(StreamWatchdog.Verdict.OK, watchdog.check(nowMillis = 9_999, startedAtMillis = 0, lastFrameAtMillis = null))
    }

    @Test
    fun noFirstFrameInTime() {
        assertEquals(
            StreamWatchdog.Verdict.CONNECT_TIMEOUT,
            watchdog.check(nowMillis = 10_000, startedAtMillis = 0, lastFrameAtMillis = null),
        )
    }

    @Test
    fun framesArriving() {
        assertEquals(StreamWatchdog.Verdict.OK, watchdog.check(nowMillis = 60_000, startedAtMillis = 0, lastFrameAtMillis = 59_000))
    }

    @Test
    fun framesStopped() {
        assertEquals(
            StreamWatchdog.Verdict.STALLED,
            watchdog.check(nowMillis = 60_000, startedAtMillis = 0, lastFrameAtMillis = 55_000),
        )
        assertEquals(StreamWatchdog.Verdict.OK, watchdog.check(nowMillis = 60_000, startedAtMillis = 0, lastFrameAtMillis = 55_001))
    }

    @Test
    fun stallUsesLastFrameNotStart() {
        // The first frame arrived late but recently: not stalled, even though start was long ago.
        assertEquals(StreamWatchdog.Verdict.OK, watchdog.check(nowMillis = 30_000, startedAtMillis = 0, lastFrameAtMillis = 29_000))
    }

    @Test
    fun clockGoingBackwardsIsOk() {
        assertEquals(StreamWatchdog.Verdict.OK, watchdog.check(nowMillis = 100, startedAtMillis = 5_000, lastFrameAtMillis = null))
        assertEquals(StreamWatchdog.Verdict.OK, watchdog.check(nowMillis = 100, startedAtMillis = 0, lastFrameAtMillis = 5_000))
    }
}
