package com.thorctl.protocol.model

/** What a controller button does while recents is open (the daemon holds the controller). */
enum class RecentsAction(val wire: String) {
    /** Left (D-pad or stick; repeats while held). */
    PREVIOUS("previous"),

    /** Right (D-pad or stick; repeats while held). */
    NEXT("next"),

    /** A or Start: open the selected app. */
    OPEN("open"),

    /** B or Back. */
    BACK("back"),

    /** X: close the selected app. */
    CLOSE("close"),

    /** Y: close every app. */
    CLOSE_ALL("close_all"),

    /** Home: go to the home screen. */
    HOME("home"),
    ;

    companion object {
        fun fromWire(value: String?): RecentsAction? = entries.firstOrNull { it.wire == value }
    }
}
