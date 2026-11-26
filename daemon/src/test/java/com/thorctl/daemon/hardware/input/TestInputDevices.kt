package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.FakeSys

/** /proc/bus/input/devices entries of a Thor, for [FakeSys]. */
object TestInputDevices {
    const val AYN_NODE = "/dev/input/event2"
    const val CONTROLLER_NODE = "/dev/input/event5"
    const val POWER_NODE = "/dev/input/event0"

    /** KEY_F24 is bit 2 of the fourth word. */
    val GPIO_KEYS = entry("gpio-keys", "kbd event2", "4 0 0 0")
    val CONTROLLER = entry(InputNodes.CONTROLLER_NAME, "event5 js0", "7fff000000000000 0 100040000000 0 0")

    /** KEY_POWER is bit 52 of the second word. */
    val POWER_KEY = entry(InputNodes.POWER_KEY_NAME, "kbd event0", "10000000000000 0")

    fun entry(name: String, handlers: String, keyBits: String): String =
        "I: Bus=0019 Vendor=0001 Product=0001 Version=0100\nN: Name=\"$name\"\nH: Handlers=$handlers \nB: KEY=$keyBits"

    fun install(sys: FakeSys, vararg entries: String) {
        sys[InputNodes.DEVICES] = entries.joinToString("\n\n")
    }
}
