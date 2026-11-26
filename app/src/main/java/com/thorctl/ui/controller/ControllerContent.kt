package com.thorctl.ui.controller

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.GamepadState

data class ControllerContent(val target: ControllerTarget, val gamepad: GamepadState)
