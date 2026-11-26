package com.thorctl.protocol.model

enum class LedMode(val wire: String) {
    SOLID("solid"),
    BREATHE("breathe"),
    RAINBOW("rainbow"),
    ;

    companion object {
        fun fromWire(value: String?): LedMode? = entries.firstOrNull { it.wire == value }
    }
}
