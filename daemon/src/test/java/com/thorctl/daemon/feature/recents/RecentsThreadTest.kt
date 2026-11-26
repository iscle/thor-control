package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.FakeClock
import com.thorctl.daemon.FakeScheduledExecutor
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentsThreadTest {
    private val clock = FakeClock()
    private val executor = FakeScheduledExecutor(clock)
    private val thread = RecentsThread(executor)
    private val ran = mutableListOf<String>()

    @Test
    fun aFailureDoesNotStopTheThread() {
        thread.run { error("boom") }
        thread.run { ran += "next" }
        executor.runDue()
        assertEquals(listOf("next"), ran)
    }

    @Test
    fun aFailingRepeatKeepsRepeating() {
        var runs = 0
        thread.repeat(100, 50) {
            runs++
            if (runs == 1) error("boom")
        }
        executor.advanceBy(200)
        assertEquals(3, runs)
    }

    @Test
    fun schedulesInOrder() {
        thread.schedule(20) { ran += "b" }
        thread.schedule(10) { ran += "a" }
        executor.advanceBy(19)
        assertEquals(listOf("a"), ran)
        executor.advanceBy(1)
        assertEquals(listOf("a", "b"), ran)
    }
}
