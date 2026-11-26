package com.thorctl.ui.fan

import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.Stats

data class FanContent(
    val profiles: List<FanProfile>,
    /** The profile chosen in the list (game mode may be running another one). */
    val active: FanProfile,
    val stats: Stats,
    val unit: TemperatureUnit,
)
