package com.thorctl.ui.keyboard

import android.app.Dialog
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.thorctl.R
import com.thorctl.di.ApplicationScope
import com.thorctl.platform.keyboard.KeyboardEditor
import com.thorctl.platform.accessibility.AccessibilityConnection
import com.thorctl.platform.overlay.OverlayOwner
import com.thorctl.platform.overlay.OverlayWindows
import com.thorctl.ui.components.ThorContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/** Puts [BottomKeyboard] in a window on the bottom screen while [KeyboardEditor.panelWanted]. */
@Inject
@SingleIn(AppScope::class)
class KeyboardWindow(
    private val editor: KeyboardEditor,
    private val windows: OverlayWindows,
    private val accessibility: AccessibilityConnection,
    private val content: ThorContent,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var dialog: Dialog? = null
    private var owner: OverlayOwner? = null

    fun start() {
        // Also when the accessibility service binds: a window that couldn't be shown before can be now.
        scope.launch {
            combine(editor.panelWanted, accessibility.bound) { wanted, _ -> wanted }.collect { if (it) show() else remove() }
        }
    }

    private fun show() {
        if (dialog != null) return
        val display = windows.bottomDisplay() ?: return
        val overlayContext = windows.contextFor(display) ?: run {
            Timber.w("no way to show the keyboard overlay")
            return
        }
        val keyboardDialog = Dialog(overlayContext.context, R.style.Theme_ThorControl_Menu)
        val window = keyboardDialog.window ?: return
        window.setType(overlayContext.type)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // Not focusable: the text field keeps focus, whichever screen it is on.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
        window.attributes = window.attributes.apply {
            title = "ThorKeyboard"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            windowAnimations = 0
        }
        val overlayOwner = OverlayOwner()
        val composeView = ComposeView(overlayContext.context)
        overlayOwner.attach(composeView)
        composeView.setContent { content { BottomKeyboard() } }
        keyboardDialog.setContentView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        keyboardDialog.setCancelable(false)
        try {
            keyboardDialog.show()
            overlayOwner.resume()
            dialog = keyboardDialog
            owner = overlayOwner
        } catch (e: Exception) {
            Timber.w(e, "show keyboard failed")
            overlayOwner.destroy()
        }
    }

    private fun remove() {
        val shownDialog = dialog ?: return
        dialog = null
        try {
            shownDialog.dismiss()
        } catch (_: Exception) {
        }
        owner?.destroy()
        owner = null
    }
}
