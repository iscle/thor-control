package com.thorctl.daemon.hardware.input

/** Turns a touchscreen off for Android, and back on (so nothing invisible gets tapped on a dark panel). */
interface Touchscreens {
    /** [name] is the touchscreen's input device name, as in /proc/bus/input/devices. */
    fun setEnabled(name: String, enabled: Boolean)
}
