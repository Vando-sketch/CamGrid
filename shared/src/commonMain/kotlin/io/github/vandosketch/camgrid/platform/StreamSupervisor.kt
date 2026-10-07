package io.github.vandosketch.camgrid.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.vandosketch.camgrid.core.ReconnectPolicy
import io.github.vandosketch.camgrid.core.StreamWatchdog
import io.github.vandosketch.camgrid.core.UrlRedactor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps a native player running: opens a [Session] through [open], maps what it reports to
 * [status], and on a failure closes it and opens a new one after the [policy]'s backoff,
 * counting down in whole seconds. Sessions that count decoded frames are also checked by
 * [watchdog], since a connection can stay up while nothing arrives. For players written in
 * another language (Swift on iOS), which only report events. Main thread only, like [scope].
 *
 * @param label used in log messages instead of the URL (the URL may contain credentials).
 */
class StreamSupervisor<S : StreamSupervisor.Session>(
    private val scope: CoroutineScope,
    private val label: String,
    private val open: (Callbacks) -> S,
    private val now: () -> Long = monotonicMillis(),
    private val policy: ReconnectPolicy = ReconnectPolicy(),
    private val watchdog: StreamWatchdog = StreamWatchdog(),
    private val pollMillis: Long = 1_000,
) {
    /** One attempt to play the stream. */
    interface Session {
        /**
         * Video frames decoded so far, or a negative number while the player cannot tell; the
         * watchdog starts with the first count that is not negative.
         */
        fun decodedFrames(): Long

        fun close()
    }

    /** What a session reports; only the current session's first failure counts. */
    interface Callbacks {
        fun onPlaying()

        /** [reason] is a short code shown on the tile, never a URL. */
        fun onFailed(reason: String)
    }

    /** Compose snapshot state. */
    var status by mutableStateOf<StreamStatus>(StreamStatus.Connecting)
        private set

    /** The current session, null while waiting to reconnect; snapshot state. */
    var session by mutableStateOf<S?>(null)
        private set

    // Identifies the current session; callbacks from older ones are ignored.
    private var generation = 0
    private var attempt = 0
    private var released = false
    private var job: Job? = null
    private var startedAt = 0L
    private var lastCount = -1L
    private var lastFrameAt: Long? = null

    fun start() {
        if (session == null && job == null && !released) connect()
    }

    fun release() {
        if (released) return
        released = true
        generation++
        job?.cancel()
        job = null
        session?.close()
        session = null
    }

    private fun connect() {
        val current = ++generation
        status = StreamStatus.Connecting
        startedAt = now()
        lastCount = -1L
        lastFrameAt = null
        val opened = try {
            open(SessionCallbacks(current))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (opened == null) {
            fail(current, "OPEN_FAILED")
            return
        }
        // The player may have failed (or been released) while it was being created.
        if (current != generation) {
            opened.close()
            return
        }
        session = opened
        job = scope.launch {
            while (true) {
                delay(pollMillis)
                checkFrames(current, opened)
            }
        }
    }

    private fun checkFrames(current: Int, from: S) {
        val count = from.decodedFrames()
        if (count < 0) return
        val time = now()
        if (count > 0 && count != lastCount) {
            lastFrameAt = time
            onPlaying(current)
        }
        lastCount = count
        when (watchdog.check(time, startedAt, lastFrameAt)) {
            StreamWatchdog.Verdict.OK -> Unit
            StreamWatchdog.Verdict.CONNECT_TIMEOUT -> fail(current, "TIMEOUT")
            StreamWatchdog.Verdict.STALLED -> fail(current, "STALLED")
        }
    }

    private fun onPlaying(from: Int) {
        if (released || from != generation) return
        attempt = 0
        if (status != StreamStatus.Playing) status = StreamStatus.Playing
    }

    private fun fail(from: Int, reason: String) {
        if (released || from != generation) return
        generation++
        job?.cancel()
        session?.close()
        session = null
        val safeReason = UrlRedactor.redact(reason)
        AppLog.w("Stream '$label' failed: $safeReason (attempt $attempt)")
        val delayMillis = policy.delayMillis(attempt)
        attempt++
        status = StreamStatus.Offline(safeReason, seconds(delayMillis))
        job = scope.launch {
            var remaining = delayMillis
            while (remaining > 0) {
                status = StreamStatus.Offline(safeReason, seconds(remaining))
                val step = minOf(remaining, 1_000L)
                delay(step)
                remaining -= step
            }
            job = null
            connect()
        }
    }

    private inner class SessionCallbacks(private val id: Int) : Callbacks {
        override fun onPlaying() = onPlaying(id)

        override fun onFailed(reason: String) = fail(id, reason)
    }

    private companion object {
        fun seconds(millis: Long) = ((millis + 999) / 1000).toInt()

        fun monotonicMillis(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }
}
