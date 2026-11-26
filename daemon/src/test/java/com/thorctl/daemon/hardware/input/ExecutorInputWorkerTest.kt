package com.thorctl.daemon.hardware.input

import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExecutorInputWorkerTest {
    private val failures = LinkedBlockingQueue<Throwable>()
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Other tests' reader threads may still log; only this worker's failures count.
            if (message.startsWith("input handler failed") && t != null) failures += t
        }
    }
    private val worker = ExecutorInputWorker()

    @BeforeTest
    fun plantTree() = Timber.plant(tree)

    @AfterTest
    fun uprootTree() = Timber.uproot(tree)

    private fun nextFailure(): Throwable = assertNotNull(failures.poll(2, TimeUnit.SECONDS), "no failure logged")

    @Test
    fun postedAndScheduledFailuresAreLoggedAndTheWorkerCarriesOn() {
        val posted = IllegalStateException("posted")
        val scheduled = IllegalStateException("scheduled")
        worker.post { throw posted }
        assertSame(posted, nextFailure())
        worker.schedule(1) { throw scheduled }
        assertSame(scheduled, nextFailure())

        val ran = CountDownLatch(1)
        worker.post { ran.countDown() }
        assertTrue(ran.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun aFailingLongHomePressIsLogged() {
        val failure = IllegalStateException("app switcher")
        val listener = object : InputWatcher.Listener by RecordingListener() {
            override fun onHomeLong() = throw failure
        }
        val home = HomeButton(homeRemapped = false, worker, listener)
        home.onKey(1)
        assertSame(failure, nextFailure())
    }

    @Test
    fun aCancelledTaskNeverRuns() {
        val ran = CountDownLatch(1)
        worker.schedule(100) { ran.countDown() }.cancel()
        assertFalse(ran.await(300, TimeUnit.MILLISECONDS))
    }
}
