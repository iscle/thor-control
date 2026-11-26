package com.thorctl.daemon.system

/** The power manager. */
interface PowerService {
    /** Whether the device is awake; true if it can't be asked. */
    fun isInteractive(): Boolean

    /**
     * User activity on [displayId]'s power group, as a touch would be: restarts its screen timeout
     * (and brightens it from dim). Each panel can be its own power group, so call it per display.
     */
    fun userActivity(displayId: Int)
}
