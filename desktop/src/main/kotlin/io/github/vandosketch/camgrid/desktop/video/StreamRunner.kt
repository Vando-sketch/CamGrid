package io.github.vandosketch.camgrid.desktop.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.vandosketch.camgrid.core.ReconnectPolicy
import io.github.vandosketch.camgrid.core.SignalingException
import io.github.vandosketch.camgrid.core.StreamWatchdog
import io.github.vandosketch.camgrid.platform.StreamStatus
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Why a stream attempt failed. [code] is short and safe to show and log (`HTTP_404`,
 * `ICE_FAILED`); it never contains the URL.
 */
class StreamFailure(val code: String) : Exception(code)

/** Told about every decoded frame, on whatever thread decodes it. */
fun interface FrameListener {
    fun onFrame()
}

/** One attempt at playing a stream: one peer connection or one RTSP session. */
interface StreamConnection {
    /**
     * Connects and plays, calling [frames] for each decoded frame, until the stream fails
     * (throws, preferably [StreamFailure] or [IOException]) or ends (returns). Cancelling the
     * caller closes the connection and frees everything it holds.
     */
    suspend fun play(frames: FrameListener)

    /** Only has an effect on a connection with audio. */
    fun setMuted(muted: Boolean) = Unit
}

/**
 * Keeps one stream playing, like the Android `WebRtcStream`: a new [StreamConnection] per
 * attempt, [StreamWatchdog] for a stream that connects but shows nothing or freezes, and
 * [ReconnectPolicy] backoff with a per-second countdown in [status]. Works for any transport.
 *
 * [scope] should run on the UI thread, since [status] is Compose state; [clock] is a
 * monotonic millisecond clock that may be called from decoder threads.
 *
 * @param label used in log messages instead of the URL (the URL may contain credentials).
 */
class StreamRunner(
    private val scope: CoroutineScope,
    private val label: String,
    private val connect: () -> StreamConnection,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val log: (String) -> Unit = { System.err.println("CamGrid: $it") },
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    private val watchdog: StreamWatchdog = StreamWatchdog(),
    private val watchdogIntervalMillis: Long = 1_000,
) {
    var status by mutableStateOf<StreamStatus>(StreamStatus.Connecting)
        private set

    private var job: Job? = null
    private var current: StreamConnection? = null
    private var muted = false
    private var released = false

    fun start() {
        check(job == null && !released) { "start() once" }
        job = scope.launch { runLoop() }
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        current?.setMuted(muted)
    }

    fun release() {
        if (released) return
        released = true
        job?.cancel()
        job = null
        current = null
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive && !released) {
            status = StreamStatus.Connecting
            val reason = attemptOnce(onPlaying = {
                attempt = 0
                status = StreamStatus.Playing
            })
            log("Stream '$label' failed: $reason (attempt $attempt)")
            var remaining = reconnectPolicy.delayMillis(attempt)
            attempt++
            // Count down in whole seconds so the tile can show "retrying in N s".
            while (remaining > 0) {
                status = StreamStatus.Offline(reason = reason, retryInSeconds = ((remaining + 999) / 1000).toInt())
                val step = minOf(remaining, 1_000L)
                delay(step)
                remaining -= step
            }
        }
    }

    /** Runs one connection until it fails, ends or the watchdog gives up; returns the reason. */
    private suspend fun attemptOnce(onPlaying: () -> Unit): String {
        val connection = connect()
        connection.setMuted(muted)
        current = connection
        val startedAt = clock()
        val lastFrameAt = AtomicLong(NO_FRAME)
        val firstFrame = CompletableDeferred<Unit>()
        val listener = FrameListener {
            lastFrameAt.set(clock())
            if (!firstFrame.isCompleted) firstFrame.complete(Unit)
        }
        return try {
            coroutineScope {
                val outcome = CompletableDeferred<String>()
                launch {
                    connection.play(listener)
                    outcome.complete("ENDED")
                }
                launch {
                    firstFrame.await()
                    onPlaying()
                }
                launch {
                    while (true) {
                        delay(watchdogIntervalMillis)
                        val last = lastFrameAt.get().takeIf { it != NO_FRAME }
                        when (watchdog.check(clock(), startedAt, last)) {
                            StreamWatchdog.Verdict.OK -> Unit
                            StreamWatchdog.Verdict.CONNECT_TIMEOUT -> outcome.complete("TIMEOUT")
                            StreamWatchdog.Verdict.STALLED -> outcome.complete("STALLED")
                        }
                    }
                }
                val reason = outcome.await()
                coroutineContext.cancelChildren()
                reason
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StreamFailure) {
            e.code
        } catch (e: SignalingException) {
            e.code
        } catch (e: Exception) {
            // Only the type: exception messages can contain the host or the full URL.
            e.javaClass.simpleName
        } finally {
            if (current === connection) current = null
        }
    }

    private companion object {
        const val NO_FRAME = Long.MIN_VALUE
    }
}
