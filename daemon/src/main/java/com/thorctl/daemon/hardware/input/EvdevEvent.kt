package com.thorctl.daemon.hardware.input

/** One evdev event to write back to a device node (see [Evdev.write]). */
data class EvdevEvent(val type: Int, val code: Int, val value: Int)
