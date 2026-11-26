package com.thorctl.daemon.hardware.input

/** One input device as /proc/bus/input/devices lists it: name, event handler (eventN) and key bitmap. */
class ProcInputDevice(val name: String, val handler: String, private val keyBits: List<Long>) {
    /** Its event node, /dev/input/eventN. */
    val path: String get() = "/dev/input/$handler"

    /** Whether the device reports key [code]. */
    fun hasKey(code: Int): Boolean {
        val wordIndex = code / 64
        return wordIndex < keyBits.size && (keyBits[wordIndex] shr (code % 64)) and 1L == 1L
    }
}
