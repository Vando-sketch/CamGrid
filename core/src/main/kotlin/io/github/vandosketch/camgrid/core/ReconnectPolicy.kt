package io.github.vandosketch.camgrid.core

/**
 * Exponential backoff for reconnecting a dropped stream: [initialDelayMillis] for attempt 0,
 * doubling each attempt, capped at [maxDelayMillis]. Negative attempts are treated as 0.
 */
class ReconnectPolicy(
    val initialDelayMillis: Long = 1_000,
    val maxDelayMillis: Long = 30_000,
) {
    init {
        require(initialDelayMillis > 0) { "initialDelayMillis must be positive" }
        require(maxDelayMillis >= initialDelayMillis) { "maxDelayMillis must be >= initialDelayMillis" }
    }

    fun delayMillis(attempt: Int): Long = TODO()
}
