package com.thorctl.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.thorctl.di.StorageScope
import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.domain.repository.DisplayPreferencesRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DataStoreDisplayPreferences(
    private val store: DataStore<Preferences>,
    @StorageScope private val scope: CoroutineScope,
) : DisplayPreferencesRepository {
    override val temperatureUnit: StateFlow<TemperatureUnit> = store.data
        .map { if (it[SettingsKeys.FAHRENHEIT] == true) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS }
        .stateIn(scope, SharingStarted.Eagerly, TemperatureUnit.CELSIUS)

    override fun setTemperatureUnit(unit: TemperatureUnit) {
        scope.launch {
            store.edit {
                it[SettingsKeys.FAHRENHEIT] = unit == TemperatureUnit.FAHRENHEIT
            }
        }
    }
}
