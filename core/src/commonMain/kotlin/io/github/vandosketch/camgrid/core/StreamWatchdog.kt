package io.github.vandosketch.camgrid.core

/**
 * Decides when a live stream that reports no error is dead anyway: no first frame within
 * [connectTimeoutMillis] of starting, or no frame for [stallTimeoutMillis] since the last one.
 * A WebRTC connection can stay "connected" while the server sends nothing, so the player
 * checks this periodically and reconnects on anything but [Verdict.OK].
 */
class StreamWatchdog(
    val connectTimeoutMillis: Long = 15_000,
    val stallTimeoutMillis: Long = 8_000,
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(stallTimeoutMillis > 0) { "stallTimeoutMillis must be positive" }
    }

    enum class Verdict { OK, CONNECT_TIMEOUT, STALLED }

    /**
     * [lastFrameAtMillis] is null until the first frame. All times come from one monotonic
     * clock; a time in the future (clock went backwards) counts as no time elapsed.
     */
    fun check(nowMillis: Long, startedAtMillis: Long, lastFrameAtMillis: Long?): Verdict = when {
        lastFrameAtMillis == null ->
            if (nowMillis - startedAtMillis >= connectTimeoutMillis) Verdict.CONNECT_TIMEOUT else Verdict.OK
        nowMillis - lastFrameAtMillis >= stallTimeoutMillis -> Verdict.STALLED
        else -> Verdict.OK
    }
}
