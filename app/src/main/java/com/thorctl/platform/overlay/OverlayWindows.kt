package com.thorctl.platform.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import com.thorctl.domain.model.DaemonStatus
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.platform.accessibility.AccessibilityConnection
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Where and how overlays (menu, HUD, keyboard, recents, blackout) get their windows. */
@Inject
@SingleIn(AppScope::class)
class OverlayWindows(
    private val context: Context,
    private val accessibility: AccessibilityConnection,
    private val daemon: DaemonRepository,
) {
    private val displays: DisplayManager get() = context.getSystemService(DisplayManager::class.java)

    fun display(id: Int): Display? = displays.getDisplay(id)

    /** The bottom screen: the daemon's word for it, or else the smaller built-in non-default display. */
    fun bottomDisplay(): Display? {
        val status = daemon.status.value
        val bottomId =
            if (status is DaemonStatus.Connected) status.state.value.bottomDisplayId else -1
        if (bottomId >= 0) {
            displays.getDisplay(bottomId)?.let { return it }
        }
        return displays.displays
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
            .minByOrNull { it.mode.physicalWidth * it.mode.physicalHeight }
    }

    /** The layers that can host an overlay right now, strongest first. */
    fun availableLayers(): List<OverlayLayer> = buildList {
        if (accessibility.service != null) add(OverlayLayer.ACCESSIBILITY)
        if (Settings.canDrawOverlays(context)) add(OverlayLayer.APPLICATION)
    }

    /** An accessibility overlay when the service is bound, else an application overlay; null if neither works. */
    fun contextFor(
        display: Display,
        layer: OverlayLayer? = availableLayers().firstOrNull()
    ): OverlayContext? {
        val service = accessibility.service
        return when (layer) {
            OverlayLayer.ACCESSIBILITY -> service?.let {
                OverlayContext(
                    it.createDisplayContext(display),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    layer
                )
            }

            OverlayLayer.APPLICATION -> {
                val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                OverlayContext(
                    context.createDisplayContext(display).createWindowContext(type, null),
                    type,
                    layer
                )
            }

            null -> null
        }
    }
}
