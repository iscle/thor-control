package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.Stats

fun StatsParcel.toModel() = Stats(
    cpuLoad = cpuLoad,
    cpuFreqMhz = cpuFreqMhz,
    clusterFreqsMhz = clusterFreqsMhz?.toList().orEmpty(),
    cpuTemp = cpuTemp,
    gpuLoad = gpuLoad,
    gpuFreqMhz = gpuFreqMhz,
    gpuTemp = gpuTemp,
    ramTotalMb = ramTotalMb,
    ramAvailMb = ramAvailMb,
    batteryLevel = batteryLevel,
    batteryStatus = batteryStatus.orEmpty(),
    batteryWatts = batteryWatts,
    inputWatts = inputWatts,
    batteryTemp = batteryTemp,
    fanPwm = fanPwm,
    fanTemp = fanTemp,
)
