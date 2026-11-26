package com.thorctl.daemon.hardware.input

/** Runs posted work right away, on the caller's thread; delayed work runs when the test calls [advance]. */
class FakeInputWorker : InputWorker {
    private class Task(val at: Long, val block: () -> Unit) {
        @Volatile
        var cancelled = false
    }

    private var now = 0L
    private val pending = ArrayList<Task>()

    /** Cancelling comes too late from now on, as when the task had already started running. */
    @Volatile
    var cancelTooLate = false

    override fun post(block: () -> Unit) = block()

    override fun schedule(delayMs: Long, block: () -> Unit): Cancellable {
        val task = synchronized(this) { Task(now + delayMs, block).also { pending += it } }
        return Cancellable { if (!cancelTooLate) task.cancelled = true }
    }

    /** Moves time on by [ms], running the delayed work that comes due and wasn't cancelled. */
    fun advance(ms: Long) {
        val due = synchronized(this) {
            now += ms
            pending.filter { it.at <= now }.sortedBy { it.at }.also { pending.removeAll(it) }
        }
        for (task in due) if (!task.cancelled) task.block()
    }
}
