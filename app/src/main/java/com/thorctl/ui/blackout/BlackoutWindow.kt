package com.thorctl.ui.blackout

import android.graphics.Color
import android.graphics.PixelFormat
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.thorctl.di.ApplicationScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.platform.overlay.OverlayWindows
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Gamepad mode with the screens dark: a black window over the whole bottom screen. That panel
 * has to stay powered (the controller's driver cuts the controller off once both panels are
 * off), but black OLED pixels give no light, use next to no power and can't burn in. The daemon
 * switches the top panel off and touch off on both meanwhile.
 */
@Inject
@SingleIn(AppScope::class)
class BlackoutWindow(
    private val daemon: DaemonRepository,
    private val windows: OverlayWindows,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var view: View? = null
    private var wm: WindowManager? = null

    fun start() {
        scope.launch {
            // Every state push, not only changes: a show that failed (no overlay layer yet) is retried.
            daemon.state
                .map { s -> s?.gamepad?.let { it.on && it.dark } == true }
                .collect { if (it) show() else hide() }
        }
    }

    private fun show() {
        if (view != null) return
        val display = windows.bottomDisplay() ?: return
        val oc = windows.contextFor(display) ?: return
        val v = View(oc.context).apply { setBackgroundColor(Color.BLACK) }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            oc.type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            title = "ThorControlBlackout"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val w = oc.context.getSystemService(WindowManager::class.java)
        try {
            w.addView(v, lp)
            view = v
            wm = w
        } catch (e: Exception) {
            Timber.w(e, "black window failed")
        }
    }

    private fun hide() {
        val v = view ?: return
        try {
            wm?.removeView(v)
        } catch (_: Exception) {
        }
        view = null
        wm = null
    }
}
