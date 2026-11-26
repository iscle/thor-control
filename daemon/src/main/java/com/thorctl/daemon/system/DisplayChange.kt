package com.thorctl.daemon.system

/** What happened to a display. */
enum class DisplayChange {
    ADDED,
    REMOVED,

    /** Its power state changed (on, off, doze...). */
    STATE,

    /** Its brightness changed (Android set its backlight again). */
    BRIGHTNESS,
}
