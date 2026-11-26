package com.thorctl.daemon.system

class FakePowerService(var interactive: Boolean = true) : PowerService {
    val userActivity = mutableListOf<Int>()

    override fun isInteractive(): Boolean = interactive

    override fun userActivity(displayId: Int) {
        userActivity += displayId
    }
}
