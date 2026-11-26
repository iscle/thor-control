package com.thorctl.protocol.model

/** Where the built-in controller's input goes. */
enum class ControllerTarget(val wire: String) {
    /** No association: the controller follows the focused screen. */
    FREE("free"),
    TOP("top"),
    BOTTOM("bottom"),
    ;

    /** The next target when the Controller tile's icon is tapped: follow focus → top → bottom → follow focus. */
    fun next(): ControllerTarget = when (this) {
        FREE -> TOP
        TOP -> BOTTOM
        BOTTOM -> FREE
    }

    companion object {
        fun fromWire(value: String?): ControllerTarget? = entries.firstOrNull { it.wire == value }
    }
}
