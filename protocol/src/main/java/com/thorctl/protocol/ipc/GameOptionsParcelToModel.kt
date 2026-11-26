package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.GameOptions

fun GameOptionsParcel?.toModel(): GameOptions {
    if (this == null) return GameOptions()
    return GameOptions(closeApps = closeApps, cpu = cpu, gpu = gpu, fan = fan.orEmpty(), bottomOff = bottomOff)
}
