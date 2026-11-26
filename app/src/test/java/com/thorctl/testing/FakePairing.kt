package com.thorctl.testing

import com.thorctl.domain.repository.GamepadPairing

class FakePairing : GamepadPairing {
    var prompts = 0

    override fun makeDiscoverable() {
        prompts++
    }
}
