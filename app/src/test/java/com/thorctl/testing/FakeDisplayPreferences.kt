package com.thorctl.testing

import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.domain.repository.DisplayPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDisplayPreferences : DisplayPreferencesRepository {
    override val temperatureUnit = MutableStateFlow(TemperatureUnit.CELSIUS)

    override fun setTemperatureUnit(unit: TemperatureUnit) {
        temperatureUnit.value = unit
    }
}
