package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportThrottleTest {
    private val clock = FakeClock()
    private val sent = mutableListOf<Int>()

    /** Flushes waiting to run, with their delay. */
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()

    private val throttle = ReportThrottle(clock, 8, schedule = { delayMs, flush -> scheduled += delayMs to flush }, send = { sent += it[0].toInt() })

    private fun submit(id: Int) = throttle.submit(byteArrayOf(id.toByte()))

    private fun runScheduled() {
        val due = scheduled.toList()
        scheduled.clear()
        due.forEach { it.second() }
    }

    @Test
    fun theFirstReportGoesAtOnce() {
        submit(1)
        assertEquals(listOf(1), sent)
        assertEquals(emptyList(), scheduled)
    }

    @Test
    fun aReportTooSoonWaitsForTheRestOfTheInterval() {
        submit(1)
        clock.now += 3
        submit(2)
        assertEquals(listOf(1), sent)
        assertEquals(listOf(5L), scheduled.map { it.first })
        clock.now += 5
        runScheduled()
        assertEquals(listOf(1, 2), sent)
    }

    @Test
    fun theNewestWinsAndOnlyOneFlushIsScheduled() {
        submit(1)
        clock.now += 1
        submit(2)
        clock.now += 1
        submit(3)
        submit(4)
        assertEquals(1, scheduled.size)
        clock.now += 6
        runScheduled()
        assertEquals(listOf(1, 4), sent)
        // Right after the flush, the next one waits a full interval again.
        submit(5)
        assertEquals(listOf(8L), scheduled.map { it.first })
    }

    @Test
    fun afterTheIntervalReportsGoAtOnce() {
        submit(1)
        clock.now += 8
        submit(2)
        clock.now += 100
        submit(3)
        assertEquals(listOf(1, 2, 3), sent)
        assertEquals(emptyList(), scheduled)
    }

    @Test
    fun aFlushWithNothingPendingSendsNothing() {
        submit(1)
        clock.now += 2
        submit(2)
        // An on-time report goes out directly and takes the pending one with it.
        clock.now += 6
        submit(3)
        assertEquals(listOf(1, 3), sent)
        runScheduled()
        assertEquals(listOf(1, 3), sent)
        // The flag was cleared: a report too soon schedules a flush again.
        submit(4)
        assertEquals(1, scheduled.size)
    }
}
