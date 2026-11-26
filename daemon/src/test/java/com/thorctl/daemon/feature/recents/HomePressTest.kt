package com.thorctl.daemon.feature.recents

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomePressTest {
    /** A capture whose result is [outcome]: the picture's success, or what get() throws. */
    private class Shot(private val outcome: () -> Boolean) : Future<Boolean> {
        var waitedMs = -1L

        override fun get(timeout: Long, unit: TimeUnit): Boolean {
            waitedMs = unit.toMillis(timeout)
            return outcome()
        }

        override fun get(): Boolean = outcome()

        override fun cancel(mayInterruptIfRunning: Boolean) = false

        override fun isCancelled() = false

        override fun isDone() = true
    }

    private fun press(shot: Future<Boolean>) = HomePress(0, null, File("x.jpg"), shot)

    @AfterTest
    fun clearInterrupt() {
        Thread.interrupted()
    }

    @Test
    fun waitsABoundedTimeForTheCapture() {
        val shot = Shot { true }
        assertTrue(press(shot).captured())
        assertEquals(HomePress.WAIT_MS, shot.waitedMs)
        assertFalse(press(CompletableFuture.completedFuture(false)).captured())
    }

    @Test
    fun aSlowOrFailedCaptureIsNoPicture() {
        assertFalse(press(Shot { throw TimeoutException() }).captured())
        assertFalse(press(Shot { throw ExecutionException(IllegalStateException()) }).captured())
    }

    @Test
    fun anInterruptIsKept() {
        assertFalse(press(Shot { throw InterruptedException() }).captured())
        assertTrue(Thread.currentThread().isInterrupted)
    }
}
