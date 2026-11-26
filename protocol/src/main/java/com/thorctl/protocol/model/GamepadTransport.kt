package com.thorctl.protocol.model

enum class GamepadTransport(val wire: String) {
    USB("usb"),
    BLUETOOTH("bt"),
    ;

    companion object {
        fun fromWire(value: String?): GamepadTransport? = entries.firstOrNull { it.wire == value }
    }
}
