package com.thorctl.daemon.hardware.input

import java.io.Closeable
import java.io.IOException

/**
 * An open evdev node (/dev/input/eventN). Behind an interface so the input logic can be fed
 * fake events in tests. Closing it wakes a blocked [readEvents] and drops a [grab]. Every
 * failure is an [IOException].
 */
interface EvdevDevice : Closeable {
    /**
     * Blocks, handing every event to [onEvent], until the node reports end of input (it went
     * away). Throws [IOException] when reading fails, which includes the device being closed
     * meanwhile; whatever [onEvent] throws passes through.
     */
    @Throws(IOException::class)
    fun readEvents(onEvent: EventHandler)

    /** Takes the device exclusively (EVIOCGRAB): nobody else, Android included, sees its events until it is closed. */
    @Throws(IOException::class)
    fun grab()

    /** The first 32 switch bits (EVIOCGSW), e.g. bit [EvdevCodes.SW_LID]. */
    @Throws(IOException::class)
    fun switches(): Int

    /** Closes it, ignoring a failure: there is nothing left to do about one. */
    fun closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
        }
    }
}
