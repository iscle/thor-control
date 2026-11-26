package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.core.Clock

/**
 * Sends reports at most once per [intervalMs], newest wins: a report that comes too soon waits
 * for the interval to pass, and any newer one replaces it meanwhile, so nothing builds a backlog.
 * [send] runs under the throttle's lock; [schedule] runs a flush after a delay (on some other
 * thread), and only one is ever outstanding.
 */
class ReportThrottle(
    private val clock: Clock,
    private val intervalMs: Long,
    private val schedule: (delayMs: Long, flush: () -> Unit) -> Unit,
    private val send: (ByteArray) -> Unit,
) {
    private val lock = Any()
    private var pending: ByteArray? = null
    private var lastSentAt = 0L
    private var flushScheduled = false

    fun submit(report: ByteArray) {
        synchronized(lock) {
            pending = report
            val wait = lastSentAt + intervalMs - clock.uptimeMillis()
            if (wait <= 0) {
                flushLocked()
            } else if (!flushScheduled) {
                flushScheduled = true
                schedule(wait) {
                    synchronized(lock) {
                        flushScheduled = false
                        flushLocked()
                    }
                }
            }
        }
    }

    private fun flushLocked() {
        val report = pending ?: return
        pending = null
        lastSentAt = clock.uptimeMillis()
        send(report)
    }
}
