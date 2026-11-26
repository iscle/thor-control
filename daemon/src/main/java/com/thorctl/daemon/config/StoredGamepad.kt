package com.thorctl.daemon.config

import com.thorctl.protocol.model.GamepadTransport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How gamepad mode connects (it always starts off). */
@Serializable
class StoredGamepad(
    @SerialName("transport") val transport: String = GamepadTransport.USB.wire,
    @SerialName("screensOff") val screensOff: Boolean = true,
)
