package com.thorctl.daemon.feature.gamepad

/**
 * Gamepad mode's stream of reports: controller events from the grab thread and restarts or
 * explicit reports from the coordinator, one at a time under one lock, so the encoder is never
 * read while it changes and reports go to [send] in the order they were made. A frame that
 * changes nothing is not sent again.
 *
 * [send] runs under the lock; it must not call back into this class.
 */
class ControllerReports(private val send: (ByteArray) -> Unit) {
    private val lock = Any()
    private val encoder = HidReportEncoder()

    /** The last report sent (empty before the first); what a freshly attached host gets first. */
    @Volatile
    var last = ByteArray(0)
        private set

    /** Grab thread: one raw evdev event. */
    fun onEvent(type: Int, code: Int, value: Int) {
        synchronized(lock) {
            val report = encoder.onEvent(type, code, value) ?: return
            if (!report.contentEquals(last)) sendLocked(report)
        }
    }

    /** Starts over from what the controller holds right now and sends that. */
    fun restart(keys: Set<Int>, axisValues: IntArray) {
        synchronized(lock) {
            encoder.load(keys, axisValues)
            sendLocked(encoder.build())
        }
    }

    /** Sends [report] as it is (everything released before the host goes away). */
    fun send(report: ByteArray) {
        synchronized(lock) { sendLocked(report) }
    }

    private fun sendLocked(report: ByteArray) {
        last = report
        send.invoke(report)
    }
}
