package com.thorctl.daemon.config

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.FanProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The daemon's settings as stored on disk (JSON). The @SerialName keys are the format: a property
 * may be renamed, its key may not; add new keys with defaults, and old files still load. Enums are
 * kept as their wire names, so a value this version doesn't know falls back to the default
 * instead of failing the whole file. The shape is the one earlier versions wrote with org.json,
 * so their config.json loads as it is.
 */

@Serializable
class StoredConfig(
    @SerialName("fanProfile") val fanProfile: String = FanProfile.SMART_ID,
    @SerialName("customProfiles") val customProfiles: List<StoredFanProfile> = emptyList(),
    @SerialName("controller") val controller: String = ControllerTarget.FREE.wire,
    @SerialName("leds") val leds: StoredLeds = StoredLeds(),
    @SerialName("gameOptions") val gameOptions: StoredGameOptions = StoredGameOptions(),
    @SerialName("gamepad") val gamepad: StoredGamepad = StoredGamepad(),
)
