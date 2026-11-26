package com.thorctl.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.thorctl.di.StorageScope
import com.thorctl.domain.repository.GamepadHostRepository
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
class DataStoreGamepadHosts(
    private val store: DataStore<Preferences>,
    @StorageScope private val scope: CoroutineScope,
) : GamepadHostRepository {
    override val lastHost: StateFlow<String?> = store.data
        .map { it[SettingsKeys.GAMEPAD_HOST] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override fun remember(address: String) {
        scope.launch { store.edit { it[SettingsKeys.GAMEPAD_HOST] = address } }
    }
}
