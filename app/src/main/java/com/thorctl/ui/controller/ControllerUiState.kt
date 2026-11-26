package com.thorctl.ui.controller

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.GamepadState

data class ControllerUiState(val target: ControllerTarget = ControllerTarget.FREE, val gamepad: GamepadState? = null)
