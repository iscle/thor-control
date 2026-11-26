package com.thorctl.daemon.hardware.input

import java.io.Closeable

/**
 * An open evdev node (/dev/input/eventN). Behind an interface so the input logic can be fed
 * fake events in tests. Closing it wakes a blocked [readEvents] and drops a [grab].
 */
interface EvdevDevice : Closeable {
    /**
     * Blocks, handing every event to [onEvent], until the node reports end of input (it went
     * away). Throws when reading fails, which includes the device being closed meanwhile.
     */
    fun readEvents(onEvent: EventHandler)

    /** Takes the device exclusively (EVIOCGRAB): nobody else, Android included, sees its events until it is closed. Throws on failure. */
    fun grab()

    /** The first 32 switch bits (EVIOCGSW), e.g. bit [EvdevCodes.SW_LID]. Throws on failure. */
    fun switches(): Int
}
