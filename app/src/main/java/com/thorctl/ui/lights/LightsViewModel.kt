package com.thorctl.ui.lights

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
    private val leds: LedState get() = daemon.state.value?.leds ?: LedState()

    private val target = MutableStateFlow(if (leds.sync) StickTarget.BOTH else StickTarget.LEFT)

    val uiState: StateFlow<LightsUiState> =
        combine(daemon.state, target) { s, t -> LightsUiState(s?.leds ?: LedState(), t) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, LightsUiState(leds, target.value))

    private fun set(s: LedState) = daemon.send(DaemonCommand.SetLeds(s))

    fun setEnabled(on: Boolean) = set(leds.copy(enabled = on))

    /** Picking a mode turns the lights on. */
    fun setMode(mode: LedMode) = set(leds.copy(mode = mode, enabled = true))

    /** Both sticks share the left one's color; one stick alone unlinks them. */
    fun setTarget(t: StickTarget) {
        target.value = t
        val l = leds
        if (t == StickTarget.BOTH && !l.sync) set(l.copy(sync = true, right = l.left))
        if (t != StickTarget.BOTH && l.sync) set(l.copy(sync = false, right = l.left))
    }

    /** [rgb] is 0xRRGGBB (anything above is ignored). */
    fun setColor(rgb: Int) {
        val c = rgb and 0xFFFFFF
        val l = leds
        set(
            when (target.value) {
                StickTarget.BOTH -> l.copy(left = c, right = c, sync = true)
                StickTarget.LEFT -> l.copy(left = c, right = l.rightColor, sync = false)
                StickTarget.RIGHT -> l.copy(right = c, sync = false)
            },
        )
    }

    /** [fraction] 0..1; never fully dark (that's what the switch is for). */
    fun setBrightness(fraction: Float) = set(leds.copy(brightness = (fraction * 255).toInt().coerceIn(MIN_BRIGHTNESS, 255)))

    /** [fraction] 0..1 over the speed range. */
    fun setSpeed(fraction: Float) =
        set(leds.copy(speed = LedState.MIN_SPEED + fraction.coerceIn(0f, 1f) * (LedState.MAX_SPEED - LedState.MIN_SPEED)))

    companion object {
        const val MIN_BRIGHTNESS = 4

        fun speedFraction(speed: Float) = (speed - LedState.MIN_SPEED) / (LedState.MAX_SPEED - LedState.MIN_SPEED)
    }
}
