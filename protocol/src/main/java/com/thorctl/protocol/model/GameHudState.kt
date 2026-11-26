package com.thorctl.protocol.model

enum class GameHudState(val wire: String) {
    ON("on"),
    OFF("off"),
    STARTED("started"),
    BOTTOM_OFF("bottom_off"),
    ;

    companion object {
        fun fromWire(value: String?): GameHudState = entries.firstOrNull { it.wire == value } ?: STARTED
    }
}
