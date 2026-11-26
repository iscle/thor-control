package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_F24
import com.thorctl.daemon.hardware.input.EvdevCodes.KEY_POWER
import dev.zacsweers.metro.Inject

/**
 * Finds the event nodes of the Thor's input devices (AYN button, built-in controller, power
 * button) in /proc/bus/input/devices. Read fresh on every call: nodes come and go.
 */
@Inject
class InputNodes(private val sys: Sys) {
    fun devices(): List<ProcInputDevice> = parse(sys.read(DEVICES))

    /** Node of the first device matching [match], or null. */
    fun find(match: (ProcInputDevice) -> Boolean): String? = devices().firstOrNull(match)?.path

    /** The AYN button (KEY_F24): normally on gpio-keys, else on any non-controller device that has the key. */
    fun aynButton(): String? =
        find { it.name == "gpio-keys" && it.hasKey(KEY_F24) }
            ?: find { it.hasKey(KEY_F24) && !it.name.contains("Controller", true) }

    fun controller(): String? = find { it.name == CONTROLLER_NAME }

    fun powerKey(): String? =
        find { it.name == POWER_KEY_NAME } ?: find { it.name.contains("pwrkey") && it.hasKey(KEY_POWER) }

    companion object {
        const val DEVICES = "/proc/bus/input/devices"
        const val CONTROLLER_NAME = "Xbox Wireless Controller"
        const val POWER_KEY_NAME = "pmic_pwrkey"

        /** Input devices from /proc/bus/input/devices: name, event node and key bitmap. */
        fun parse(text: String?): List<ProcInputDevice> {
            if (text == null) return emptyList()
            return text.split(Regex("\n\\s*\n")).mapNotNull { block ->
                val name = Regex("N: Name=\"([^\"]*)\"").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val handler = Regex("H: Handlers=.*?(event\\d+)").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val bits = Regex("B: KEY=([0-9a-f ]+)").find(block)?.groupValues?.get(1)
                    ?.trim()?.split(' ')?.reversed()?.map { it.toULong(16).toLong() }
                    ?: emptyList()
                ProcInputDevice(name, handler, bits)
            }
        }
    }
}
