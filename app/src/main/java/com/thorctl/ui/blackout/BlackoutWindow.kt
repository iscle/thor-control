package com.thorctl.ui.blackout

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.thorctl.domain.model.DaemonStatus
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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
    private var windowManager: WindowManager? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            // Every state push, not only changes: a show that failed (no overlay layer yet) is retried.
            daemon.status
                .mapLatest { status ->
                    when (status) {
                        DaemonStatus.Disconnected -> false
                        is DaemonStatus.Connected -> status.state.value.let { state -> state.gamepad.on && state.gamepad.dark }
                    }
                }
                .collect { dark -> if (dark) show() else hide() }
        }
    }

    private fun show() {
        if (view != null) return
        val display = windows.bottomDisplay() ?: return
        val overlayContext = windows.contextFor(display) ?: return
        val blackView = View(overlayContext.context).apply { setBackgroundColor(Color.BLACK) }
        val layoutParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            overlayContext.type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            title = "ThorControlBlackout"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val displayWindowManager = overlayContext.context.getSystemService(WindowManager::class.java)
        try {
            displayWindowManager.addView(blackView, layoutParams)
            view = blackView
            windowManager = displayWindowManager
        } catch (e: Exception) {
            Timber.w(e, "black window failed")
        }
    }

    private fun hide() {
        val shownView = view ?: return
        try {
            windowManager?.removeView(shownView)
        } catch (_: Exception) {
        }
        view = null
        windowManager = null
    }
}
