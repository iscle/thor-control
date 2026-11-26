package com.thorctl.domain.repository

import com.thorctl.domain.model.TemperatureUnit
import kotlinx.coroutines.flow.StateFlow

/** How the app shows things (kept by the app, not the daemon). */
interface DisplayPreferencesRepository {
    val temperatureUnit: StateFlow<TemperatureUnit>

    fun setTemperatureUnit(unit: TemperatureUnit)
}
