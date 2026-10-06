package io.github.vandosketch.camgrid.desktop.video

import io.github.vandosketch.camgrid.core.ReconnectPolicy
import io.github.vandosketch.camgrid.core.SignalingException
import io.github.vandosketch.camgrid.core.StreamWatchdog
import io.github.vandosketch.camgrid.platform.StreamStatus
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class StreamRunnerTest {

    private class FakeConnection : StreamConnection {
        var frames: FrameListener? = null
        val outcome = CompletableDeferred<Unit>()
        var cancelled = false
        var muted: Boolean? = null

        override suspend fun play(frames: FrameListener) {
            this.frames = frames
            try {
                outcome.await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }

        override fun setMuted(muted: Boolean) {
            this.muted = muted
        }

        fun frame() = frames!!.onFrame()
        fun fail(e: Exception) = outcome.completeExceptionally(e)
        fun end() = outcome.complete(Unit)
    }

    private class Harness(scope: TestScope) {
        val connections = mutableListOf<FakeConnection>()
        val logs = mutableListOf<String>()
        val runner = StreamRunner(
            scope = scope.backgroundScope,
            label = "Front door",
            connect = { FakeConnection().also(connections::add) },
            clock = { scope.testScheduler.currentTime },
            log = logs::add,
            reconnectPolicy = ReconnectPolicy(initialDelayMillis = 1_000, maxDelayMillis = 30_000),
            watchdog = StreamWatchdog(connectTimeoutMillis = 15_000, stallTimeoutMillis = 8_000),
        )
        val current get() = connections.last()
    }

    private fun TestScope.started(): Harness = Harness(this).also {
        it.runner.start()
        runCurrent()
    }

    @Test
    fun connectingUntilTheFirstFrameThenPlaying() = runTest {
        val h = started()
        assertEquals(StreamStatus.Connecting, h.runner.status)
        assertEquals(1, h.connections.size)
        h.current.frame()
        runCurrent()
        assertEquals(StreamStatus.Playing, h.runner.status)
    }

    @Test
    fun failureShowsTheCodeAndRetriesAfterTheBackoff() = runTest {
        val h = started()
        h.current.fail(StreamFailure("HTTP_404"))
        runCurrent()
        assertEquals(StreamStatus.Offline("HTTP_404", 1), h.runner.status)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, h.connections.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, h.connections.size)
        assertEquals(StreamStatus.Connecting, h.runner.status)
    }

    @Test
    fun backoffDoublesAndCountsDownInSeconds() = runTest {
        val h = started()
        h.current.fail(StreamFailure("X"))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        h.current.fail(StreamFailure("X"))
        runCurrent()
        assertEquals(StreamStatus.Offline("X", 2), h.runner.status)
        advanceTimeBy(2_000)
        runCurrent()
        h.current.fail(StreamFailure("X"))
        runCurrent()
        assertEquals(StreamStatus.Offline("X", 4), h.runner.status)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(StreamStatus.Offline("X", 3), h.runner.status)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(StreamStatus.Offline("X", 1), h.runner.status)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(4, h.connections.size)
    }

    @Test
    fun aFrameResetsTheBackoff() = runTest {
        val h = started()
        h.current.fail(StreamFailure("X"))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        h.current.fail(StreamFailure("X"))
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        h.current.frame()
        runCurrent()
        h.current.fail(StreamFailure("ICE_FAILED"))
        runCurrent()
        assertEquals(StreamStatus.Offline("ICE_FAILED", 1), h.runner.status)
    }

    @Test
    fun noFirstFrameTimesOut() = runTest {
        val h = started()
        advanceTimeBy(14_000)
        runCurrent()
        assertEquals(StreamStatus.Connecting, h.runner.status)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(StreamStatus.Offline("TIMEOUT", 1), h.runner.status)
        assertTrue(h.connections[0].cancelled)
    }

    @Test
    fun frozenVideoIsDetectedAsStalled() = runTest {
        val h = started()
        advanceTimeBy(3_000)
        h.current.frame()
        runCurrent()
        assertEquals(StreamStatus.Playing, h.runner.status)
        advanceTimeBy(7_000)
        runCurrent()
        assertEquals(StreamStatus.Playing, h.runner.status)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(StreamStatus.Offline("STALLED", 1), h.runner.status)
        assertTrue(h.connections[0].cancelled)
    }

    @Test
    fun steadyFramesKeepPlaying() = runTest {
        val h = started()
        repeat(60) {
            h.current.frame()
            advanceTimeBy(1_000)
            runCurrent()
        }
        assertEquals(StreamStatus.Playing, h.runner.status)
        assertEquals(1, h.connections.size)
    }

    @Test
    fun aStreamThatEndsIsRetried() = runTest {
        val h = started()
        h.current.frame()
        h.current.end()
        runCurrent()
        assertEquals(StreamStatus.Offline("ENDED", 1), h.runner.status)
    }

    @Test
    fun ioErrorsReportOnlyTheirTypeNeverTheMessage() = runTest {
        val h = started()
        h.current.fail(IOException("connect to rtsp://user:secret@192.0.2.10 failed"))
        runCurrent()
        assertEquals(StreamStatus.Offline("IOException", 1), h.runner.status)
        assertFalse(h.logs.any { "secret" in it || "192.0.2.10" in it }, h.logs.toString())
        assertTrue(h.logs.any { "Front door" in it })
    }

    @Test
    fun signallingErrorsShowTheirCode() = runTest {
        val h = started()
        h.current.fail(SignalingException(SignalingException.Reason.HTTP_STATUS, "HTTP_404"))
        runCurrent()
        assertEquals(StreamStatus.Offline("HTTP_404", 1), h.runner.status)
    }

    @Test
    fun unexpectedExceptionsAlsoReconnect() = runTest {
        val h = started()
        h.current.fail(IllegalStateException("native failure"))
        runCurrent()
        assertEquals(StreamStatus.Offline("IllegalStateException", 1), h.runner.status)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, h.connections.size)
    }

    @Test
    fun releaseStopsTheConnectionAndAllRetries() = runTest {
        val h = started()
        h.runner.release()
        runCurrent()
        assertTrue(h.connections[0].cancelled)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, h.connections.size)
    }

    @Test
    fun releaseDuringBackoffStopsRetrying() = runTest {
        val h = started()
        h.current.fail(StreamFailure("X"))
        runCurrent()
        h.runner.release()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, h.connections.size)
    }

    @Test
    fun muteReachesTheCurrentAndLaterConnections() = runTest {
        val h = started()
        h.runner.setMuted(true)
        assertEquals(true, h.current.muted)
        h.current.fail(StreamFailure("X"))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(true, h.current.muted)
        h.runner.setMuted(false)
        assertEquals(false, h.current.muted)
    }
}
