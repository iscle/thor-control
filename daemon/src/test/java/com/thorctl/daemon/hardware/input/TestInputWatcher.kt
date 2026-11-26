package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.FakeSys

/** An [InputWatcher] over fakes, wired as the daemon's graph wires the real one. */
object TestInputWatcher {
    fun create(sys: FakeSys, evdev: FakeEvdev = FakeEvdev(), worker: FakeInputWorker = FakeInputWorker()): InputWatcher {
        val nodes = InputNodes(sys)
        val controller = ControllerInput(sys, nodes, evdev, worker)
        val lidPowerKey = LidPowerKey(nodes, evdev, worker)
        return InputWatcher(controller, AynButton(nodes, evdev, controller, lidPowerKey, worker), lidPowerKey)
    }
}
