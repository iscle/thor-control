package com.thorctl.daemon.hardware.input

/** Access to evdev nodes: open one to read (and grab) it, or inject events into it. */
interface Evdev {
    /** Opens the node at [path] for reading; throws if it can't. */
    fun open(path: String): EvdevDevice

    /** Writes [events] to the node at [path] in one go, as if the device had reported them. Throws on failure. */
    fun write(path: String, events: List<EvdevEvent>)
}
