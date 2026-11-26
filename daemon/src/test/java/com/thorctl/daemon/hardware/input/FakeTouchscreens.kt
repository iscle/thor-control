package com.thorctl.daemon.hardware.input

class FakeTouchscreens : Touchscreens {
    /** Names of the touchscreens turned off. */
    val disabled = HashSet<String>()

    override fun setEnabled(name: String, enabled: Boolean) {
        if (enabled) disabled -= name else disabled += name
    }
}
