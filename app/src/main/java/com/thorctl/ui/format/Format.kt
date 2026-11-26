package com.thorctl.ui.format

import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.GamepadState
import com.thorctl.protocol.model.Stats
import kotlin.math.roundToInt

/** The words and numbers the screens show. */
object Format {
    fun temperature(celsius: Float, unit: TemperatureUnit): String =
        if (unit == TemperatureUnit.FAHRENHEIT) "${(celsius * 9f / 5f + 32f).roundToInt()} °F" else "${celsius.toInt()} °C"

    /** Megabytes as "1.5 GB". */
    fun gigabytes(mb: Int): String = "${"%.1f".format(mb / 1024f)} GB"

    fun percent(fraction: Float): String = "${(fraction * 100).toInt()}%"

    fun controller(target: ControllerTarget?): String = when (target) {
        ControllerTarget.TOP -> "Top screen"
        ControllerTarget.BOTTOM -> "Bottom screen"
        ControllerTarget.FREE -> "Follows focus"
        null -> "Unavailable"
    }

    fun gamepadSummary(g: GamepadState): String = when {
        !g.on -> "Off"
        g.error.isNotEmpty() -> g.error
        g.connected && g.isBluetooth -> "Connected to ${g.host.ifEmpty { "a device" }}"
        g.connected -> "Connected over USB"
        g.isBluetooth -> "Bluetooth · waiting for a device"
        else -> "USB · waiting for a computer"
    }

    /** What to do next, under the gamepad status. */
    fun gamepadHint(g: GamepadState): String = when {
        !g.on && g.isBluetooth -> "Turn it on, then connect from the other device"
        !g.on -> "Turn it on, then plug the Thor into the other device"
        g.error.isNotEmpty() -> "Try Bluetooth instead"
        g.connected -> "The controller now drives that device. AYN turns the screens back on"
        g.isBluetooth -> "Pair the Thor from the other device's Bluetooth settings, or wait for the last one"
        else -> "Connect the Thor's USB-C port to the other device"
    }

    fun powerValue(s: Stats): String = when {
        s.charging -> "+${"%.1f".format(s.batteryWatts)} W"
        s.batteryWatts > 0.05f -> "${"%.1f".format(s.batteryWatts)} W"
        s.plugged -> "${"%.1f".format(s.inputWatts)} W"
        else -> "0.0 W"
    }

    fun powerDetail(s: Stats, unit: TemperatureUnit): String = when {
        s.charging -> "Charging · ${s.batteryLevel}% · in ${"%.1f".format(s.inputWatts)} W"
        s.plugged -> "Plugged in · ${s.batteryLevel}%"
        else -> "On battery · ${s.batteryLevel}% · ${temperature(s.batteryTemp, unit)}"
    }

    fun cpuDetail(s: Stats, unit: TemperatureUnit): String = "${"%.2f".format(s.cpuFreqMhz / 1000f)} GHz · ${temperature(s.cpuTemp, unit)}"

    fun gpuDetail(s: Stats, unit: TemperatureUnit): String = "${s.gpuFreqMhz} MHz · ${temperature(s.gpuTemp, unit)}"

    fun memoryValue(s: Stats): String = gigabytes(s.ramUsedMb)

    fun memoryDetail(s: Stats): String = "of ${"%.0f".format(s.ramTotalMb / 1024f)} GB · ${(s.ramUsedFraction * 100).toInt()}% used"

    fun fanDescription(p: FanProfile): String = when (p.id) {
        FanProfile.AUTO_ID -> "LineageOS thermal control"
        "quiet" -> "Low noise, runs warmer"
        FanProfile.SMART_ID -> "Balanced"
        "sport" -> "Cool and loud"
        "max" -> "Full speed"
        else -> "Custom curve"
    }
}
