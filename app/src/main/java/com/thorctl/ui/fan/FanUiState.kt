package com.thorctl.ui.fan

import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.Stats

data class FanUiState(
    val profiles: List<FanProfile> = FanProfile.BUILTINS,
    /** The profile chosen in the list (game mode may be running another one). */
    val active: FanProfile = FanProfile.BUILTINS.first(),
    val stats: Stats? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
)
