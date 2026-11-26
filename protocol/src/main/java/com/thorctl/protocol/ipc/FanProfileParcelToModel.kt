package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.FanPoint
import com.thorctl.protocol.model.FanProfile

fun FanProfileParcel.toModel(): FanProfile {
    val t = temps ?: FloatArray(0)
    val p = percents ?: FloatArray(0)
    return FanProfile(id.orEmpty(), name ?: "Custom", (0 until minOf(t.size, p.size)).map { FanPoint(t[it], p[it]) })
}
