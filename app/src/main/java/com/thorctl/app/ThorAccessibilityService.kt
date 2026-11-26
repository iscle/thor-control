package com.thorctl.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/*
 * Android components, at the names Android settings and the Magisk scripts know them by. They
 * only hand over to the graph.
 */

/**
 * Exists only to give overlays the accessibility overlay window layer. It requests no key
 * filtering and no window content, so it never sits in the input path of games.
 */
class ThorAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        graph.accessibility.onConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        graph.accessibility.onDisconnected(this)
        return false
    }

    override fun onDestroy() {
        graph.accessibility.onDisconnected(this)
        super.onDestroy()
    }
}
