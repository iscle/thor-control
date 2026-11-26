package com.thorctl.ui.menu

import com.thorctl.domain.model.TemperatureUnit
import com.thorctl.protocol.model.DaemonState
import com.thorctl.protocol.model.Stats

data class MenuUiState(
    val state: DaemonState? = null,
    val stats: Stats? = null,
    val connected: Boolean = false,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    /** The boosted game's name, or "". */
    val gameLabel: String = "",
) {
    /** Controls work only with the daemon connected and its state known. */
    val enabled: Boolean get() = connected && state != null
}
