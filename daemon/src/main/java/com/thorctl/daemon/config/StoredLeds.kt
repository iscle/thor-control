package com.thorctl.daemon.config

import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class StoredLeds(
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("mode") val mode: String = LedMode.SOLID.wire,
    @SerialName("left") val left: Int = LedState().left,
    @SerialName("right") val right: Int = LedState().right,
    @SerialName("sync") val sync: Boolean = true,
    @SerialName("brightness") val brightness: Int = LedState().brightness,
    @SerialName("speed") val speed: Float = 1f,
    @SerialName("offWhenAsleep") val offWhenAsleep: Boolean = true,
) {
    fun toModel() = LedState(
        enabled = enabled,
        mode = LedMode.fromWire(mode) ?: LedMode.SOLID,
        left = left and 0xFFFFFF,
        right = right and 0xFFFFFF,
        sync = sync,
        brightness = brightness.coerceIn(0, 255),
        speed = speed.coerceIn(LedState.MIN_SPEED, LedState.MAX_SPEED),
        offWhenAsleep = offWhenAsleep,
    )

    companion object {
        fun of(state: LedState) = StoredLeds(state.enabled, state.mode.wire, state.left, state.right, state.sync, state.brightness, state.speed, state.offWhenAsleep)
    }
}
