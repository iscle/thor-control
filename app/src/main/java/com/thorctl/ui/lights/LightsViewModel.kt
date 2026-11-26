package com.thorctl.ui.lights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorctl.domain.model.DaemonStatus
import com.thorctl.domain.model.ifConnected
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import com.thorctl.ui.state.DaemonUiState
import com.thorctl.ui.state.currentUiState
import com.thorctl.ui.state.stateUiStates
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
            .flatMapLatest { (status, stick) ->
                status.stateUiStates { state ->
                    LightsContent(
                        state.leds,
                        stick
                    )
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                daemon.status.value.currentUiState { state ->
                    LightsContent(
                        state.leds,
                        target.value
                    )
                })

    /** Changes the daemon's current lights; nothing while disconnected. */
    private fun update(change: (LedState) -> LedState) = daemon.status.value.ifConnected {
        val next = change(it.leds)
        if (next != it.leds) daemon.send(DaemonCommand.SetLeds(next))
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }

    /** Picking a mode turns the lights on. */
    fun setMode(mode: LedMode) = update { it.copy(mode = mode, enabled = true) }

    /** Both sticks share the left one's color; one stick alone unlinks them. */
    fun setTarget(stick: StickTarget) {
        target.value = stick
        update { leds ->
            when {
                stick == StickTarget.BOTH && !leds.sync -> leds.copy(sync = true, right = leds.left)
                stick != StickTarget.BOTH && leds.sync -> leds.copy(sync = false, right = leds.left)
                else -> leds
            }
        }
    }

    /** [rgb] is 0xRRGGBB (anything above is ignored). */
    fun setColor(rgb: Int) {
        val color = rgb and 0xFFFFFF
        update { leds ->
            when (target.value) {
                StickTarget.BOTH -> leds.copy(left = color, right = color, sync = true)
                StickTarget.LEFT -> leds.copy(left = color, right = leds.rightColor, sync = false)
                StickTarget.RIGHT -> leds.copy(right = color, sync = false)
            }
        }
    }

    /** [fraction] 0..1; never fully dark (that's what the switch is for). */
    fun setBrightness(fraction: Float) =
        update { it.copy(brightness = (fraction * 255).toInt().coerceIn(MIN_BRIGHTNESS, 255)) }

    /** [fraction] 0..1 over the speed range. */
    fun setSpeed(fraction: Float) =
        update {
            it.copy(
                speed = LedState.MIN_SPEED + fraction.coerceIn(
                    0f,
                    1f
                ) * (LedState.MAX_SPEED - LedState.MIN_SPEED)
            )
        }

    companion object {
        const val MIN_BRIGHTNESS = 4

        fun speedFraction(speed: Float) =
            (speed - LedState.MIN_SPEED) / (LedState.MAX_SPEED - LedState.MIN_SPEED)
    }
}
