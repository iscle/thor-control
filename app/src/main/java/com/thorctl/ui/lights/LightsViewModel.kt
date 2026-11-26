package com.thorctl.ui.lights

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.thorctl.ui.state.uiStates
import com.thorctl.ui.state.currentUiState
import com.thorctl.domain.model.ifConnected
import com.thorctl.ui.state.DaemonUiState
import com.thorctl.domain.model.DaemonStatus
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class LightsViewModel(private val daemon: DaemonRepository) : ViewModel() {
    /** Linked sticks start on "Both", unlinked ones on the left stick. */
    private val target = MutableStateFlow(
        when (val status = daemon.status.value) {
            DaemonStatus.Disconnected -> StickTarget.BOTH
            is DaemonStatus.Connected -> if (status.state.value.leds.sync) StickTarget.BOTH else StickTarget.LEFT
        },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DaemonUiState<LightsContent>> =
        combine(daemon.status, target) { status, stick -> status to stick }
            .flatMapLatest { (status, stick) -> status.uiStates { state, _ -> LightsContent(state.leds, stick) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, daemon.status.value.currentUiState { state, _ -> LightsContent(state.leds, target.value) })

    /** Changes the daemon's current lights; nothing while disconnected. */
    private fun update(change: (LedState) -> LedState) = daemon.status.value.ifConnected {
        val next = change(it.leds)
        if (next != it.leds) daemon.send(DaemonCommand.SetLeds(next))
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }

    /** Picking a mode turns the lights on. */
    fun setMode(mode: LedMode) = update { it.copy(mode = mode, enabled = true) }

    /** Both sticks share the left one's color; one stick alone unlinks them. */
    fun setTarget(t: StickTarget) {
        target.value = t
        update { l ->
            when {
                t == StickTarget.BOTH && !l.sync -> l.copy(sync = true, right = l.left)
                t != StickTarget.BOTH && l.sync -> l.copy(sync = false, right = l.left)
                else -> l
            }
        }
    }

    /** [rgb] is 0xRRGGBB (anything above is ignored). */
    fun setColor(rgb: Int) {
        val c = rgb and 0xFFFFFF
        update { l ->
            when (target.value) {
                StickTarget.BOTH -> l.copy(left = c, right = c, sync = true)
                StickTarget.LEFT -> l.copy(left = c, right = l.rightColor, sync = false)
                StickTarget.RIGHT -> l.copy(right = c, sync = false)
            }
        }
    }

    /** [fraction] 0..1; never fully dark (that's what the switch is for). */
    fun setBrightness(fraction: Float) = update { it.copy(brightness = (fraction * 255).toInt().coerceIn(MIN_BRIGHTNESS, 255)) }

    /** [fraction] 0..1 over the speed range. */
    fun setSpeed(fraction: Float) =
        update { it.copy(speed = LedState.MIN_SPEED + fraction.coerceIn(0f, 1f) * (LedState.MAX_SPEED - LedState.MIN_SPEED)) }

    companion object {
        const val MIN_BRIGHTNESS = 4

        fun speedFraction(speed: Float) = (speed - LedState.MIN_SPEED) / (LedState.MAX_SPEED - LedState.MIN_SPEED)
    }
}
