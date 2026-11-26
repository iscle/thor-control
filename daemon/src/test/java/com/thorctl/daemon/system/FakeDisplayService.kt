package com.thorctl.daemon.system

class FakeDisplayService(var displays: List<DisplayInfo> = emptyList()) : DisplayService {
    val brightness = HashMap<Int, Float>()
    val powerRequests = mutableListOf<Pair<Int, Int>>()
    var listener: DisplayChangeListener? = null

    override fun displays(): List<DisplayInfo> = displays

    override fun brightness(displayId: Int): Float = brightness[displayId] ?: 0.5f

    override fun setBrightness(displayId: Int, value: Float) {
        brightness[displayId] = value
    }

    override fun requestDisplayPower(displayId: Int, state: Int): Boolean {
        powerRequests += displayId to state
        return true
    }

    override fun watch(listener: DisplayChangeListener) {
        this.listener = listener
    }
}
