package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.FanPoint
import com.thorctl.protocol.model.FanProfile

fun FanProfileParcel.toModel(): FanProfile {
    val temperatures = temps ?: FloatArray(0)
    val duties = percents ?: FloatArray(0)
    return FanProfile(id.orEmpty(), name ?: "Custom", (0 until minOf(temperatures.size, duties.size)).map { index -> FanPoint(temperatures[index], duties[index]) })
}
