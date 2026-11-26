package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.FanProfile

/*
 * Models <-> AIDL parcelables. Readers fall back to defaults for anything missing or unknown, so
 * a field added on one side never crashes the other.
 */

fun FanProfile.toParcel() = FanProfileParcel().also {
    it.id = id
    it.name = name
    it.temps = points.map { point -> point.temp }.toFloatArray()
    it.percents = points.map { point -> point.percent }.toFloatArray()
}
