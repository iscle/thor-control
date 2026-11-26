package com.thorctl.protocol.ipc

import com.thorctl.protocol.model.GameOptions

fun GameOptions.toParcel() = GameOptionsParcel().also {
    it.closeApps = closeApps
    it.cpu = cpu
    it.gpu = gpu
    it.fan = fan
    it.bottomOff = bottomOff
}
