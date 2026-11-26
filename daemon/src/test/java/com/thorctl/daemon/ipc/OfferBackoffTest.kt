package com.thorctl.daemon.ipc

import com.thorctl.daemon.FakeClock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfferBackoffTest {
    private val clock = FakeClock()
    private val backoff = OfferBackoff(clock)

    @Test
    fun failuresSpaceTheOffersOutUpToHalfAMinute() {
        assertTrue(backoff.ready())
        for (waitMs in listOf(2000L, 4000L, 8000L, 16_000L, 30_000L, 30_000L)) {
            backoff.failed()
            clock.now += waitMs - 1
            assertFalse(backoff.ready())
            clock.now += 1
            assertTrue(backoff.ready())
        }
    }

    @Test
    fun aSuccessStartsOver() {
        repeat(3) { backoff.failed() }
        backoff.succeeded()
        assertTrue(backoff.ready())
        backoff.failed()
        clock.now += 2000
        assertTrue(backoff.ready())
    }
}
