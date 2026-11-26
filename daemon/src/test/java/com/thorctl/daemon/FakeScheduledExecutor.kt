package com.thorctl.daemon

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A single-threaded executor that runs nothing until told to: [runDue] runs what is due at
 * [clock]'s time, [advanceBy] moves the clock and runs each task at its time, in order.
 */
class FakeScheduledExecutor(private val clock: FakeClock) : AbstractExecutorService(), ScheduledExecutorService {
    private val queue = mutableListOf<Task<*>>()
    private var nextOrder = 0L
    private var shutdown = false

    /** Tasks waiting to run (repeating ones included), cancelled ones left out. */
    val pending: Int get() = queue.count { !it.isCancelled }

    fun runDue() = advanceBy(0)

    fun advanceBy(ms: Long) {
        val until = clock.now + ms
        while (true) {
            val task = queue.filter { !it.isCancelled && it.at <= until }.minWithOrNull(compareBy<Task<*>>({ it.at }, { it.order })) ?: break
            queue.remove(task)
            clock.now = maxOf(clock.now, task.at)
            task.fire()
        }
        queue.removeAll { it.isCancelled }
        clock.now = until
    }

    private fun <V> add(delayMs: Long, periodMs: Long, callable: Callable<V>): Task<V> =
        Task(clock.now + delayMs, periodMs, callable).also { queue += it }

    override fun execute(command: Runnable) {
        add(0, 0, Executors.callable(command))
    }

    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
        add(unit.toMillis(delay), 0, Executors.callable(command))

    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> =
        add(unit.toMillis(delay), 0, callable)

    override fun scheduleAtFixedRate(command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*> =
        add(unit.toMillis(initialDelay), unit.toMillis(period), Executors.callable(command))

    override fun scheduleWithFixedDelay(command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
        add(unit.toMillis(initialDelay), unit.toMillis(delay), Executors.callable(command))

    override fun shutdown() {
        shutdown = true
    }

    override fun shutdownNow(): List<Runnable> {
        shutdown = true
        return emptyList()
    }

    override fun isShutdown(): Boolean = shutdown

    override fun isTerminated(): Boolean = shutdown

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true

    private inner class Task<V>(var at: Long, private val periodMs: Long, callable: Callable<V>) : FutureTask<V>(callable), ScheduledFuture<V> {
        val order = nextOrder++

        fun fire() {
            if (periodMs == 0L) {
                run()
            } else if (runAndReset()) {
                at = clock.now + periodMs
                queue += this
            }
        }

        override fun getDelay(unit: TimeUnit): Long = unit.convert(at - clock.now, TimeUnit.MILLISECONDS)

        override fun compareTo(other: Delayed): Int = getDelay(TimeUnit.MILLISECONDS).compareTo(other.getDelay(TimeUnit.MILLISECONDS))
    }
}
