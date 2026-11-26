package com.thorctl.protocol.model

/** One of the Thor's two built-in screens. */
enum class Screen(val wire: String) {
    TOP("top"),
    BOTTOM("bottom"),
    ;

    companion object {
        fun fromWire(value: String?): Screen? = entries.firstOrNull { it.wire == value }
    }
}
