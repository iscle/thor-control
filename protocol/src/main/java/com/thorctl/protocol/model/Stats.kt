package com.thorctl.protocol.model

import kotlin.math.roundToInt

/** Live telemetry, pushed once per second while a client is subscribed. */
data class Stats(
    val cpuLoad: Float,
    val cpuFreqMhz: Int,
    val clusterFreqsMhz: List<Int>,
    val cpuTemp: Float,
    val gpuLoad: Float,
    val gpuFreqMhz: Int,
    val gpuTemp: Float,
    val ramTotalMb: Int,
    val ramAvailMb: Int,
    val batteryLevel: Int,
    val batteryStatus: String,
    /** Power flowing out of (discharging) or into (charging) the battery, always positive. */
    val batteryWatts: Float,
    /** Power drawn from the USB port, 0 when unplugged. */
    val inputWatts: Float,
    val batteryTemp: Float,
    val fanPwm: Int,
    val fanTemp: Float,
) {
    val charging: Boolean get() = batteryStatus.equals("Charging", true)
    val plugged: Boolean get() = inputWatts > 0.05f
    val fanPercent: Int get() = (fanPwm * 100f / 255f).roundToInt()
    val ramUsedMb: Int get() = ramTotalMb - ramAvailMb

    /** Share of memory in use, 0..1. */
    val ramUsedFraction: Float get() = if (ramTotalMb > 0) ramUsedMb.toFloat() / ramTotalMb else 0f
}
