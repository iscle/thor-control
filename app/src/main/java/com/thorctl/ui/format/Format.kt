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
    fun gigabytes(megabytes: Int): String = "${"%.1f".format(megabytes / 1024f)} GB"

    fun percent(fraction: Float): String = "${(fraction * 100).toInt()}%"

    fun controller(target: ControllerTarget?): String = when (target) {
        ControllerTarget.TOP -> "Top screen"
        ControllerTarget.BOTTOM -> "Bottom screen"
        ControllerTarget.FREE -> "Follows focus"
        null -> "Unavailable"
    }

    fun gamepadSummary(gamepad: GamepadState): String = when {
        !gamepad.on -> "Off"
        gamepad.error.isNotEmpty() -> gamepad.error
        gamepad.connected && gamepad.isBluetooth -> "Connected to ${gamepad.host.ifEmpty { "a device" }}"
        gamepad.connected -> "Connected over USB"
        gamepad.isBluetooth -> "Bluetooth · waiting for a device"
        else -> "USB · waiting for a computer"
    }

    /** What to do next, under the gamepad status. */
    fun gamepadHint(gamepad: GamepadState): String = when {
        !gamepad.on && gamepad.isBluetooth -> "Turn it on, then connect from the other device"
        !gamepad.on -> "Turn it on, then plug the Thor into the other device"
        gamepad.error.isNotEmpty() -> "Try Bluetooth instead"
        gamepad.connected -> "The controller now drives that device. AYN turns the screens back on"
        gamepad.isBluetooth -> "Pair the Thor from the other device's Bluetooth settings, or wait for the last one"
        else -> "Connect the Thor's USB-C port to the other device"
    }

    fun powerValue(stats: Stats): String = when {
        stats.charging -> "+${"%.1f".format(stats.batteryWatts)} W"
        stats.batteryWatts > 0.05f -> "${"%.1f".format(stats.batteryWatts)} W"
        stats.plugged -> "${"%.1f".format(stats.inputWatts)} W"
        else -> "0.0 W"
    }

    fun powerDetail(stats: Stats, unit: TemperatureUnit): String = when {
        stats.charging -> "Charging · ${stats.batteryLevel}% · in ${"%.1f".format(stats.inputWatts)} W"
        stats.plugged -> "Plugged in · ${stats.batteryLevel}%"
        else -> "On battery · ${stats.batteryLevel}% · ${temperature(stats.batteryTemp, unit)}"
    }

    fun cpuDetail(stats: Stats, unit: TemperatureUnit): String =
        "${"%.2f".format(stats.cpuFreqMhz / 1000f)} GHz · ${temperature(stats.cpuTemp, unit)}"

    fun gpuDetail(stats: Stats, unit: TemperatureUnit): String =
        "${stats.gpuFreqMhz} MHz · ${temperature(stats.gpuTemp, unit)}"

    fun memoryValue(stats: Stats): String = gigabytes(stats.ramUsedMb)

    fun memoryDetail(stats: Stats): String =
        "of ${"%.0f".format(stats.ramTotalMb / 1024f)} GB · ${(stats.ramUsedFraction * 100).toInt()}% used"

    fun fanDescription(profile: FanProfile): String = when (profile.id) {
        FanProfile.AUTO_ID -> "LineageOS thermal control"
        "quiet" -> "Low noise, runs warmer"
        FanProfile.SMART_ID -> "Balanced"
        "sport" -> "Cool and loud"
        "max" -> "Full speed"
        else -> "Custom curve"
    }
}
