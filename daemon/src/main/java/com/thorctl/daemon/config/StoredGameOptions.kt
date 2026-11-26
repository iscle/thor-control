package com.thorctl.daemon.config

import com.thorctl.protocol.model.GameOptions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class StoredGameOptions(
    @SerialName("closeApps") val closeApps: Boolean = true,
    @SerialName("cpu") val cpu: Boolean = true,
    @SerialName("gpu") val gpu: Boolean = true,
    @SerialName("fan") val fan: String = GameOptions().fan,
    @SerialName("bottomOff") val bottomOff: Boolean = true,
) {
    fun toModel() = GameOptions(closeApps, cpu, gpu, fan, bottomOff)

    companion object {
        fun of(o: GameOptions) = StoredGameOptions(o.closeApps, o.cpu, o.gpu, o.fan, o.bottomOff)
    }
}
