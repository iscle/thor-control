package com.thorctl.daemon.system

class FakeInputService(var devices: List<InputDeviceInfo> = emptyList()) : InputService {
    val associations = HashMap<String, String>()

    /** (keyCode, displayId) of every injected key press, in order. */
    val injectedKeys = mutableListOf<Pair<Int, Int>>()

    override fun devices(): List<InputDeviceInfo> = devices

    override fun associate(descriptor: String, displayUniqueId: String) {
        associations[descriptor] = displayUniqueId
    }

    override fun removeAssociation(descriptor: String) {
        associations -= descriptor
    }

    override fun injectKey(keyCode: Int, displayId: Int) {
        injectedKeys += keyCode to displayId
    }
}
