package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.Stats

fun Stats.toParcel() = StatsParcel().also {
    it.cpuLoad = cpuLoad
    it.cpuFreqMhz = cpuFreqMhz
    it.clusterFreqsMhz = clusterFreqsMhz.toIntArray()
    it.cpuTemp = cpuTemp
    it.gpuLoad = gpuLoad
    it.gpuFreqMhz = gpuFreqMhz
    it.gpuTemp = gpuTemp
    it.ramTotalMb = ramTotalMb
    it.ramAvailMb = ramAvailMb
    it.batteryLevel = batteryLevel
    it.batteryStatus = batteryStatus
    it.batteryWatts = batteryWatts
    it.inputWatts = inputWatts
    it.batteryTemp = batteryTemp
    it.fanPwm = fanPwm
    it.fanTemp = fanTemp
}
