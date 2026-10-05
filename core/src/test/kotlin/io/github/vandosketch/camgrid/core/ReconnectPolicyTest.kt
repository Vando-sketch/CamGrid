package io.github.vandosketch.camgrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReconnectPolicyTest {

    @Test
    fun defaults() {
        val policy = ReconnectPolicy()
        assertEquals(1_000L, policy.initialDelayMillis)
        assertEquals(30_000L, policy.maxDelayMillis)
    }

    @Test
    fun attemptZeroIsInitialDelay() {
        assertEquals(1_000L, ReconnectPolicy().delayMillis(0))
        assertEquals(250L, ReconnectPolicy(initialDelayMillis = 250, maxDelayMillis = 10_000).delayMillis(0))
    }

    @Test
    fun doublesEachAttempt() {
        val policy = ReconnectPolicy()
        assertEquals(2_000L, policy.delayMillis(1))
        assertEquals(4_000L, policy.delayMillis(2))
        assertEquals(8_000L, policy.delayMillis(3))
        assertEquals(16_000L, policy.delayMillis(4))
    }

    @Test
    fun cappedAtMax() {
        val policy = ReconnectPolicy()
        assertEquals(30_000L, policy.delayMillis(5))
        assertEquals(30_000L, policy.delayMillis(6))
        assertEquals(30_000L, policy.delayMillis(20))
    }

    @Test
    fun cappedForHugeAttemptsWithoutOverflow() {
        val policy = ReconnectPolicy()
        assertEquals(30_000L, policy.delayMillis(62))
        assertEquals(30_000L, policy.delayMillis(63))
        assertEquals(30_000L, policy.delayMillis(64))
        assertEquals(30_000L, policy.delayMillis(1_000))
        assertEquals(30_000L, policy.delayMillis(Int.MAX_VALUE))
    }

    @Test
    fun capThatIsNotAPowerOfTwoMultiple() {
        val policy = ReconnectPolicy(initialDelayMillis = 1_000, maxDelayMillis = 5_000)
        assertEquals(1_000L, policy.delayMillis(0))
        assertEquals(2_000L, policy.delayMillis(1))
        assertEquals(4_000L, policy.delayMillis(2))
        assertEquals(5_000L, policy.delayMillis(3))
        assertEquals(5_000L, policy.delayMillis(10))
    }

    @Test
    fun maxEqualToInitialIsConstant() {
        val policy = ReconnectPolicy(initialDelayMillis = 500, maxDelayMillis = 500)
        assertEquals(500L, policy.delayMillis(0))
        assertEquals(500L, policy.delayMillis(1))
        assertEquals(500L, policy.delayMillis(50))
    }

    @Test
    fun negativeAttemptsTreatedAsZero() {
        val policy = ReconnectPolicy()
        assertEquals(1_000L, policy.delayMillis(-1))
        assertEquals(1_000L, policy.delayMillis(-100))
        assertEquals(1_000L, policy.delayMillis(Int.MIN_VALUE))
    }

    @Test
    fun rejectsNonPositiveInitialDelay() {
        assertThrows(IllegalArgumentException::class.java) { ReconnectPolicy(initialDelayMillis = 0) }
        assertThrows(IllegalArgumentException::class.java) { ReconnectPolicy(initialDelayMillis = -1) }
    }

    @Test
    fun rejectsMaxBelowInitial() {
        assertThrows(IllegalArgumentException::class.java) {
            ReconnectPolicy(initialDelayMillis = 2_000, maxDelayMillis = 1_999)
        }
    }

    @Test
    fun acceptsMaxEqualToInitial() {
        ReconnectPolicy(initialDelayMillis = 1, maxDelayMillis = 1)
    }
}
