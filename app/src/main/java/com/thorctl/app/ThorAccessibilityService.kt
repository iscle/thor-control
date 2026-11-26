package com.thorctl.app

import dev.zacsweers.metrox.android.ServiceKey
import com.thorctl.platform.accessibility.AccessibilityConnection
import android.app.Service
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.AppScope
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Exists only to give overlays the accessibility overlay window layer. It requests no key
 * filtering and no window content, so it never sits in the input path of games.
 */
@Inject
@ServiceKey(ThorAccessibilityService::class)
@ContributesIntoMap(AppScope::class, binding = binding<Service>())
class ThorAccessibilityService(private val accessibility: AccessibilityConnection) : AccessibilityService() {
    override fun onServiceConnected() {
        accessibility.onConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        accessibility.onDisconnected(this)
        return false
    }

    override fun onDestroy() {
        accessibility.onDisconnected(this)
        super.onDestroy()
    }
}
