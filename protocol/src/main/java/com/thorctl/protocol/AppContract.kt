package com.thorctl.protocol

/**
 * Names the daemon, the app and the Magisk scripts agree on. The Android components keep these
 * names across versions: Android stores them in settings (the chosen keyboard, the enabled
 * accessibility services) and the scripts address them directly.
 */
object AppContract {
    const val PACKAGE = "com.thorctl"
    const val ACCESSIBILITY_SERVICE = "com.thorctl/com.thorctl.app.ThorAccessibilityService"
    const val COMMAND_RECEIVER = "com.thorctl/.app.CommandReceiver"
    const val ACTION_TOGGLE_MENU = "com.thorctl.action.TOGGLE_MENU"
    const val ACTION_HIDE_MENU = "com.thorctl.action.HIDE_MENU"
}
