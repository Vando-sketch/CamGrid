package io.github.vandosketch.camgrid.platform

import io.github.vandosketch.camgrid.core.ReconnectPolicy
import io.github.vandosketch.camgrid.core.StreamWatchdog
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

@OptIn(ExperimentalCoroutinesApi::class)
class StreamSupervisorTest {

    /** A native player: counts frames when [frames] is set, records whether it was closed. */
    private class FakeSession(val callbacks: StreamSupervisor.Callbacks) : StreamSupervisor.Session {
        var frames = -1L
        var closed = false
        override fun decodedFrames(): Long = frames
        override fun close() {
            closed = true
        }
    }

    private val scope = TestScope()
    private val sessions = mutableListOf<FakeSession>()
    private var onOpen: (StreamSupervisor.Callbacks) -> Unit = {}
    private var openThrows = false
    private val warnings = mutableListOf<String>()
    private lateinit var previousSink: AppLog.Sink

    private fun supervisor() = StreamSupervisor(
        scope = scope,
        label = "kitchen",
        open = { callbacks ->
            if (openThrows) throw IllegalStateException("no player")
            onOpen(callbacks)
            FakeSession(callbacks).also { sessions += it }
        },
        now = { scope.testScheduler.currentTime },
        policy = ReconnectPolicy(initialDelayMillis = 1_000, maxDelayMillis = 4_000),
        watchdog = StreamWatchdog(connectTimeoutMillis = 15_000, stallTimeoutMillis = 8_000),
    ).also { it.start() }

    @BeforeTest
    fun captureLog() {
        previousSink = AppLog.sink
        AppLog.sink = AppLog.Sink { _, _, message -> warnings += message }
    }

    @AfterTest
    fun restoreLog() {
        AppLog.sink = previousSink
    }

    @Test
    fun startsConnectingWithOneSession() {
        val s = supervisor()
        assertEquals(StreamStatus.Connecting, s.status)
        assertEquals(1, sessions.size)
        assertSame(sessions[0], s.session)
    }

    @Test
    fun playingWhenThePlayerSaysSo() {
        val s = supervisor()
        sessions[0].callbacks.onPlaying()
        assertEquals(StreamStatus.Playing, s.status)
    }

    @Test
    fun failureClosesTheSessionCountsDownAndReconnects() {
        val s = supervisor()
        sessions[0].callbacks.onFailed("ERROR")
        assertTrue(sessions[0].closed)
        assertNull(s.session)
        assertEquals(StreamStatus.Offline("ERROR", 1), s.status)

        scope.advanceTimeBy(1_001)
        assertEquals(2, sessions.size)
        assertSame(sessions[1], s.session)
        assertEquals(StreamStatus.Connecting, s.status)
        assertTrue(warnings.single().contains("kitchen"))
    }

    @Test
    fun backoffGrowsUntilPlayingResetsIt() {
        val s = supervisor()
        sessions[0].callbacks.onFailed("A")
        scope.advanceTimeBy(1_001)
        sessions[1].callbacks.onFailed("B")
        assertEquals(StreamStatus.Offline("B", 2), s.status)
        scope.advanceTimeBy(1_001)
        // Counting down in whole seconds.
        assertEquals(StreamStatus.Offline("B", 1), s.status)
        scope.advanceTimeBy(1_001)
        assertEquals(3, sessions.size)

        sessions[2].callbacks.onPlaying()
        sessions[2].callbacks.onFailed("C")
        assertEquals(StreamStatus.Offline("C", 1), s.status)
    }

    @Test
    fun callbacksOfAReplacedSessionAreIgnored() {
        val s = supervisor()
        val old = sessions[0]
        old.callbacks.onFailed("A")
        scope.advanceTimeBy(1_001)
        old.callbacks.onPlaying()
        old.callbacks.onFailed("LATE")
        assertEquals(StreamStatus.Connecting, s.status)
        assertEquals(false, sessions[1].closed)
        // A second failure of the same session is ignored as well.
        sessions[1].callbacks.onFailed("B")
        sessions[1].callbacks.onFailed("B again")
        assertEquals(StreamStatus.Offline("B", 2), s.status)
    }

    @Test
    fun failureWhileOpeningIsRetried() {
        var first = true
        onOpen = { callbacks ->
            if (first) {
                first = false
                callbacks.onFailed("INVALID_URL")
            }
        }
        val s = supervisor()
        assertTrue(sessions[0].closed)
        assertNull(s.session)
        assertEquals(StreamStatus.Offline("INVALID_URL", 1), s.status)
        scope.advanceTimeBy(1_001)
        assertEquals(StreamStatus.Connecting, s.status)
        assertSame(sessions[1], s.session)
    }

    @Test
    fun aPlayerThatCannotBeCreatedIsRetried() {
        openThrows = true
        val s = supervisor()
        assertEquals(StreamStatus.Offline("OPEN_FAILED", 1), s.status)
        openThrows = false
        scope.advanceTimeBy(1_001)
        assertEquals(1, sessions.size)
        assertEquals(StreamStatus.Connecting, s.status)
    }

    @Test
    fun decodedFramesMeanPlaying() {
        val s = supervisor()
        sessions[0].frames = 0
        scope.advanceTimeBy(1_001)
        assertEquals(StreamStatus.Connecting, s.status)
        sessions[0].frames = 3
        scope.advanceTimeBy(1_000)
        assertEquals(StreamStatus.Playing, s.status)
    }

    @Test
    fun noFirstFrameTimesOut() {
        val s = supervisor()
        sessions[0].frames = 0
        scope.advanceTimeBy(14_500)
        assertEquals(StreamStatus.Connecting, s.status)
        scope.advanceTimeBy(1_000)
        assertEquals(StreamStatus.Offline("TIMEOUT", 1), s.status)
        assertTrue(sessions[0].closed)
    }

    @Test
    fun framesStoppingIsAStall() {
        val s = supervisor()
        sessions[0].frames = 10
        scope.advanceTimeBy(1_001)
        assertEquals(StreamStatus.Playing, s.status)
        scope.advanceTimeBy(7_000)
        assertEquals(StreamStatus.Playing, s.status)
        sessions[0].frames = 11
        scope.advanceTimeBy(7_500)
        assertEquals(StreamStatus.Playing, s.status)
        scope.advanceTimeBy(2_000)
        assertEquals(StreamStatus.Offline("STALLED", 1), s.status)
    }

    @Test
    fun playersThatCannotCountFramesHaveNoWatchdog() {
        val s = supervisor()
        sessions[0].callbacks.onPlaying()
        scope.advanceTimeBy(120_000)
        assertEquals(StreamStatus.Playing, s.status)
        assertEquals(1, sessions.size)
    }

    @Test
    fun releaseClosesAndStopsReconnecting() {
        val s = supervisor()
        sessions[0].callbacks.onFailed("A")
        s.release()
        scope.advanceTimeBy(60_000)
        assertEquals(1, sessions.size)

        val t = supervisor()
        val session = sessions[1]
        t.release()
        assertTrue(session.closed)
        assertNull(t.session)
        session.callbacks.onFailed("LATE")
        scope.runCurrent()
        assertEquals(2, sessions.size)
    }
}
