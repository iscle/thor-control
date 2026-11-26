package com.thorctl.protocol

/**
 * Names the daemon, the app and the Magisk scripts agree on. The Android components keep these
 * names across versions: Android stores them in settings (the chosen keyboard, the enabled
 * accessibility services) and the scripts address them directly.
 */
object AppContract {
    const val PACKAGE = "com.thorctl"
    const val ACCESSIBILITY_SERVICE = "com.thorctl/com.thorctl.app.ThorAccessibilityService"
}
