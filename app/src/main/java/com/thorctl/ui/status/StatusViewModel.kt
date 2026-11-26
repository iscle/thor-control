package com.thorctl.ui.status

import com.thorctl.domain.model.DaemonStatus
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.di.AppFlags
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.platform.accessibility.AccessibilityConnection
import com.thorctl.platform.overlay.OverlayMenu
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The app's own screen: is everything Thor Control needs in place? */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class StatusViewModel(
    daemon: DaemonRepository,
    accessibility: AccessibilityConnection,
    private val menu: OverlayMenu,
    flags: AppFlags,
) : ViewModel() {
    val uiState: StateFlow<StatusUiState> =
        combine(daemon.status, accessibility.bound, menu.shownAs) { status, bound, layer ->
            StatusUiState(status is DaemonStatus.Connected, bound, layer, flags.system, flags.persistent)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, StatusUiState(systemApp = flags.system, persistent = flags.persistent))

    fun showMenu() = menu.show()
}
