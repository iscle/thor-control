package com.thorctl.ui.gamepad

import com.thorctl.protocol.model.GamepadState

data class GamepadUiState(val gamepad: GamepadState = GamepadState(), val enabled: Boolean = false)
