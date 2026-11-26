package com.thorctl.domain.model

/** What the menu's battery pill shows. */
data class BatteryStatus(val level: Int, val charging: Boolean) {
    companion object {
        val UNKNOWN = BatteryStatus(0, false)
    }
}
