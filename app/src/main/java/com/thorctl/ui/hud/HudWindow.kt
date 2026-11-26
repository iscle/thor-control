package com.thorctl.ui.hud

import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.thorctl.di.ApplicationScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.platform.haptics.Haptics
import com.thorctl.platform.overlay.OverlayOwner
import com.thorctl.platform.overlay.OverlayWindows
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.ui.theme.ThorTheme
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import timber.log.Timber

/** The short confirmation pill for AYN shortcuts, on the screen the shortcut acted on. */
@Inject
@SingleIn(AppScope::class)
class HudWindow(
    private val daemon: DaemonRepository,
    private val windows: OverlayWindows,
    private val mapper: HudMessageMapper,
    private val haptics: Haptics,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val main = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var owner: OverlayOwner? = null
    private var displayId = -1
    private val hud = MutableStateFlow<HudMessage?>(null)
    private val hide = Runnable { hud.value = null }

    fun start() {
        scope.launch {
            daemon.events.filterIsInstance<DaemonEvent.Hud>().collect { show(mapper.map(it.hud, it.displayId)) }
        }
    }

    private fun show(message: HudMessage) {
        val display = windows.display(message.displayId) ?: windows.display(Display.DEFAULT_DISPLAY) ?: return
        if (view != null && displayId != display.displayId) remove()
        if (view == null && !add(display)) return
        haptics.tick()
        hud.value = message
        main.removeCallbacks(hide)
        main.postDelayed(hide, 1800)
    }

    private fun add(display: Display): Boolean {
        val overlayContext = windows.contextFor(display) ?: return false
        val displayWindowManager = overlayContext.context.getSystemService(WindowManager::class.java)
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayContext.type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "ThorControlHud"
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (28 * overlayContext.context.resources.displayMetrics.density).toInt()
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        val overlayOwner = OverlayOwner()
        val composeView = ComposeView(overlayContext.context)
        overlayOwner.attach(composeView)
        composeView.setContent { ThorTheme { HudPill(hud, onGone = { remove() }) } }
        return try {
            displayWindowManager.addView(composeView, layoutParams)
            overlayOwner.resume()
            view = composeView
            windowManager = displayWindowManager
            owner = overlayOwner
            displayId = display.displayId
            true
        } catch (e: Exception) {
            Timber.w(e, "add HUD failed")
            overlayOwner.destroy()
            false
        }
    }

    private fun remove() {
        val shownView = view ?: return
        view = null
        try {
            windowManager?.removeViewImmediate(shownView)
        } catch (_: Exception) {
        }
        owner?.destroy()
        owner = null
        windowManager = null
        displayId = -1
    }
}
