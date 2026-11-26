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
    private var wm: WindowManager? = null
    private var owner: OverlayOwner? = null
    private var displayId = -1
    private val hud = MutableStateFlow<HudMessage?>(null)
    private val hide = Runnable { hud.value = null }

    fun start() {
        scope.launch {
            daemon.events.filterIsInstance<DaemonEvent.Hud>().collect { show(mapper.map(it.hud, it.displayId)) }
        }
    }

    private fun show(msg: HudMessage) {
        val display = windows.display(msg.displayId) ?: windows.display(Display.DEFAULT_DISPLAY) ?: return
        if (view != null && displayId != display.displayId) remove()
        if (view == null && !add(display)) return
        haptics.tick()
        hud.value = msg
        main.removeCallbacks(hide)
        main.postDelayed(hide, 1800)
    }

    private fun add(display: Display): Boolean {
        val oc = windows.contextFor(display) ?: return false
        val windowManager = oc.context.getSystemService(WindowManager::class.java)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            oc.type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "ThorControlHud"
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (28 * oc.context.resources.displayMetrics.density).toInt()
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        val o = OverlayOwner()
        val v = ComposeView(oc.context)
        o.attach(v)
        v.setContent { ThorTheme { HudPill(hud, onGone = { remove() }) } }
        return try {
            windowManager.addView(v, lp)
            o.resume()
            view = v
            wm = windowManager
            owner = o
            displayId = display.displayId
            true
        } catch (e: Exception) {
            Timber.w(e, "add HUD failed")
            o.destroy()
            false
        }
    }

    private fun remove() {
        val v = view ?: return
        view = null
        try {
            wm?.removeViewImmediate(v)
        } catch (_: Exception) {
        }
        owner?.destroy()
        owner = null
        wm = null
        displayId = -1
    }
}
