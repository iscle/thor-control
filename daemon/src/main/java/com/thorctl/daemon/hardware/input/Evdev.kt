package com.thorctl.daemon.hardware.input

import java.io.IOException

/** Access to evdev nodes: open one to read (and grab) it, or inject events into it. Every failure is an [IOException]. */
interface Evdev {
    /** Opens the node at [path] for reading. */
    @Throws(IOException::class)
    fun open(path: String): EvdevDevice

    /** Writes [events] to the node at [path] in one go, as if the device had reported them. */
    @Throws(IOException::class)
    fun write(path: String, events: List<EvdevEvent>)
}
