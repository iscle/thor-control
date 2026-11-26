package com.thorctl.ui.menu

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.thorctl.domain.model.ifConnected
import com.thorctl.domain.model.DaemonStatus
import android.app.ActivityOptions
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.thorctl.R
import com.thorctl.di.ApplicationScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.platform.accessibility.AccessibilityConnection
import com.thorctl.platform.overlay.OverlayLayer
import com.thorctl.platform.overlay.OverlayMenu
import com.thorctl.platform.overlay.OverlayOwner
import com.thorctl.platform.overlay.OverlayWindows
import com.thorctl.protocol.model.DaemonEvent
import com.thorctl.ui.components.ThorContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Shows the menu on the bottom display.
 *
 * Preferred layer is the accessibility overlay (via our accessibility service's display
 * context): it is above all apps, the status bar and the IME, and cannot be hidden by apps that
 * use HIDE_OVERLAY_WINDOWS. If the service is not bound it falls back to an application overlay.
 *
 * The menu is hosted in a Dialog window. Dialog windows have a DecorView, which gives the public
 * Window.setBackgroundBlurRadius() blur-region path. That path works on the secondary (bottom)
 * display, unlike FLAG_BLUR_BEHIND, and animating it needs no window relayout.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MenuWindow(
    private val app: Context,
    private val daemon: DaemonRepository,
    private val windows: OverlayWindows,
    private val accessibility: AccessibilityConnection,
    private val content: ThorContent,
    @ApplicationScope private val scope: CoroutineScope,
) : OverlayMenu {
    private var view: ComposeView? = null
    private var dialog: Dialog? = null
    private var owner: OverlayOwner? = null
    private var lastBlur = -1

    /** Drives the enter/exit animation; the window is removed after the exit animation. */
    private val visible = MutableStateFlow(false)

    private val _shownAs = MutableStateFlow<OverlayLayer?>(null)
    override val shownAs: StateFlow<OverlayLayer?> = _shownAs

    private val isShowing: Boolean get() = view != null && visible.value

    fun start() {
        scope.launch { daemon.events.filterIsInstance<DaemonEvent.MenuButton>().collect { toggle() } }
        scope.launch { bottomScreenOff().collect { off -> if (off) removeNow() } }
        scope.launch {
            accessibility.bound.drop(1).collect { bound ->
                if (bound && view != null && _shownAs.value != OverlayLayer.ACCESSIBILITY) {
                    // Re-host an open menu in the stronger window layer.
                    removeNow()
                    show()
                } else if (!bound && _shownAs.value == OverlayLayer.ACCESSIBILITY) {
                    removeNow()
                }
            }
        }
    }

    /** The daemon turned the bottom screen off (never while disconnected: then it's unknown). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun bottomScreenOff(): Flow<Boolean> = daemon.status.flatMapLatest { status ->
        when (status) {
            DaemonStatus.Disconnected -> flowOf(false)
            is DaemonStatus.Connected -> status.state.map { !it.bottomOn }
        }
    }

    override fun toggle() {
        if (isShowing) hide() else show()
    }

    override fun show() {
        if (view != null) {
            visible.value = true
            return
        }
        val status = daemon.status.value
        if (status is DaemonStatus.Connected && !status.state.value.bottomOn) return
        val display = windows.bottomDisplay() ?: run {
            Timber.w("no bottom display")
            return
        }
        // Visible before the first composition so the entrance animation (not the exit) runs.
        visible.value = true
        for (layer in windows.availableLayers()) {
            if (tryAdd(display, layer)) {
                _shownAs.value = layer
                daemon.subscribeStats(true)
                return
            }
        }
        visible.value = false
        _shownAs.value = null
        Timber.w("could not show the menu: no accessibility service and no overlay permission")
    }

    override fun hide() {
        visible.value = false
    }

    private fun tryAdd(display: Display, layer: OverlayLayer): Boolean {
        val oc = windows.contextFor(display, layer) ?: return false
        val blur = oc.context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
        val d = Dialog(oc.context, R.style.Theme_ThorControl_Menu)
        val w = d.window ?: return false
        w.setType(oc.type)
        w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        w.setFormat(PixelFormat.TRANSLUCENT)
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // Not focusable: tapping the menu must not steal window focus from the game on top.
        w.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
        w.attributes = w.attributes.apply {
            title = "ThorControlMenu"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            windowAnimations = 0
        }
        w.setBackgroundBlurRadius(0)

        val o = OverlayOwner()
        val v = ComposeView(oc.context)
        o.attach(v)
        v.setContent {
            content {
                MenuOverlay(
                    visible = visible,
                    blur = blur,
                    onReveal = { p -> if (blur) setBlur(d, p) },
                    onExitFinished = { removeNow() },
                    onOpenApp = { openApp() },
                )
            }
        }
        d.setContentView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        d.setCancelable(false)
        return try {
            d.show()
            o.resume()
            dialog = d
            view = v
            owner = o
            Timber.i("menu shown on display ${display.displayId} as $layer (blur=$blur)")
            true
        } catch (e: Exception) {
            Timber.w(e, "show menu as $layer failed")
            o.destroy()
            false
        }
    }

    private fun setBlur(d: Dialog, progress: Float) {
        if (dialog != null && dialog !== d) return
        val r = (progress.coerceIn(0f, 1f) * MAX_BLUR_PX).toInt()
        if (r == lastBlur) return
        lastBlur = r
        d.window?.setBackgroundBlurRadius(r)
    }

    private fun removeNow() {
        val d = dialog ?: return
        dialog = null
        view = null
        lastBlur = -1
        try {
            d.dismiss()
        } catch (e: Exception) {
            Timber.w(e, "dismiss")
        }
        owner?.destroy()
        owner = null
        visible.value = false
        daemon.subscribeStats(false)
    }

    /** The full app, on the top screen. */
    private fun openApp() {
        hide()
        val i = Intent().setClassName(app, MAIN_ACTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opts = ActivityOptions.makeBasic()
        daemon.status.value.ifConnected { state -> if (state.topDisplayId >= 0) opts.launchDisplayId = state.topDisplayId }
        try {
            app.startActivity(i, opts.toBundle())
        } catch (e: Exception) {
            Timber.w(e, "open app")
        }
    }

    private companion object {
        /** Max backdrop blur, in px, reached at the end of the reveal animation. */
        const val MAX_BLUR_PX = 150
        const val MAIN_ACTIVITY = "com.thorctl.app.MainActivity"
    }
}
