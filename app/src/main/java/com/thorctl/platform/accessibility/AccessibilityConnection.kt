package com.thorctl.platform.accessibility

import android.accessibilityservice.AccessibilityService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * The bound accessibility service, which gives overlays the accessibility overlay window layer
 * (above all apps, the status bar and the keyboard, and not hidden by HIDE_OVERLAY_WINDOWS).
 */
@Inject
@SingleIn(AppScope::class)
class AccessibilityConnection {
    @Volatile
    var service: AccessibilityService? = null
        private set

    private val _bound = MutableStateFlow(false)
    val bound: StateFlow<Boolean> = _bound

    fun onConnected(s: AccessibilityService) {
        service = s
        _bound.value = true
        Timber.i("accessibility service connected")
    }

    fun onDisconnected(s: AccessibilityService) {
        if (service !== s) return
        service = null
        _bound.value = false
        Timber.i("accessibility service disconnected")
    }
}
