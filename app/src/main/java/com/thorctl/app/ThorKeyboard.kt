package com.thorctl.app

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.Display
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.thorctl.platform.keyboard.ImeHost
import com.thorctl.platform.keyboard.KeyboardEditor
import com.thorctl.platform.overlay.OverlayOwner
import com.thorctl.ui.components.ThorContent
import com.thorctl.ui.keyboard.UprightKeyboard
import dev.zacsweers.metro.Inject

/**
 * The system keyboard service. Android always puts a keyboard window on the screen of the text
 * field being edited. Normally ours is empty and zero-height there (apps are neither covered nor
 * resized) and the real keyboard, with its touchpad, is drawn on the bottom screen
 * (`KeyboardWindow`). Held upright (the top screen in portrait, where the bottom screen is off to
 * the side), the keys appear in this window instead, as an ordinary keyboard under the app. Both
 * live in this persistent process, so keys go straight into [getCurrentInputConnection].
 */
class ThorKeyboard : InputMethodService() {
    @Inject lateinit var editor: KeyboardEditor

    @Inject lateinit var content: ThorContent

    private var root: FrameLayout? = null
    private var inlineView: ComposeView? = null
    private var owner: OverlayOwner? = null

    /** Keys are in this window (top screen, portrait). */
    private var inline = false

    private val host = object : ImeHost {
        override val currentInputConnection: InputConnection? get() = this@ThorKeyboard.currentInputConnection

        override fun sendKeyChar(c: Char) = this@ThorKeyboard.sendKeyChar(c)

        override fun sendDownUpKeyEvents(keyCode: Int) = this@ThorKeyboard.sendDownUpKeyEvents(keyCode)

        override fun requestHideSelf(flags: Int) = this@ThorKeyboard.requestHideSelf(flags)
    }

    override fun onCreate() {
        graph.inject(this)
        super.onCreate()
        editor.attach(host)
    }

    override fun onDestroy() {
        dropInline()
        editor.detach(host)
        super.onDestroy()
    }

    /** Called again after every configuration change (rotation), which also restarts the view. */
    override fun onCreateInputView(): View {
        dropInline()
        return FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }.also { root = it }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    /** Bottom-screen mode: takes no space and no touches on the editing screen. */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (inline) return
        val h = window?.window?.decorView?.height ?: 0
        outInsets.contentTopInsets = h
        outInsets.visibleTopInsets = h
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.setEmpty()
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        editor.onStartInput(attribute)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inline = uprightTopScreen()
        if (inline) showInline() else dropInline()
        editor.onShow(info, inline)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        editor.onHide()
        super.onFinishInputView(finishingInput)
    }

    /** This keyboard window is on the top screen and that screen is in portrait. */
    private fun uprightTopScreen(): Boolean {
        val keyboardDisplay = try {
            display
        } catch (_: Exception) {
            null
        } ?: return false
        return keyboardDisplay.displayId == Display.DEFAULT_DISPLAY &&
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    private fun showInline() {
        val container = root ?: return
        if (inlineView != null) return
        val overlayOwner = OverlayOwner()
        val composeView = ComposeView(this)
        overlayOwner.attach(composeView)
        // Compose looks for its lifecycle from the window's root view as well.
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(overlayOwner)
            decorView.setViewTreeSavedStateRegistryOwner(overlayOwner)
            decorView.setViewTreeViewModelStoreOwner(overlayOwner)
        }
        composeView.setContent { content { UprightKeyboard() } }
        container.addView(composeView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        overlayOwner.resume()
        inlineView = composeView
        owner = overlayOwner
    }

    private fun dropInline() {
        inlineView?.let { root?.removeView(it) }
        inlineView = null
        owner?.destroy()
        owner = null
    }

    /**
     * L1 / R1 are shift while typing. The daemon reads them from the controller (whichever screen
     * the controller is routed to); when they also reach us, swallow them so the app doesn't react.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (isShoulder(keyCode) && editor.isShown) true else super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (isShoulder(keyCode) && editor.isShown) true else super.onKeyUp(keyCode, event)

    private fun isShoulder(keyCode: Int) = keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        editor.onSelection(newSelStart, newSelEnd)
    }
}
