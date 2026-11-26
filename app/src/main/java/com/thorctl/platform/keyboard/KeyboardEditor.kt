package com.thorctl.platform.keyboard

import com.thorctl.domain.model.DaemonStatus
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.thorctl.di.ApplicationScope
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.platform.haptics.Haptics
import com.thorctl.protocol.model.DaemonCommand
import com.thorctl.protocol.model.DaemonEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Thor Keyboard's logic. The keyboard service reports when typing starts and stops; this decides
 * where the keys go (the service's own window when the Thor is held upright, otherwise a window
 * on the bottom screen, whichever screen the text field is on) and turns key presses, touchpad
 * swipes and the select / copy / paste buttons into edits through the service's input connection.
 */
@Inject
@SingleIn(AppScope::class)
class KeyboardEditor(
    private val context: Context,
    private val daemon: DaemonRepository,
    private val haptics: Haptics,
    @ApplicationScope private val scope: CoroutineScope,
) : KeyboardActions {
    private val _ui = MutableStateFlow(KeyboardUi())
    val ui: StateFlow<KeyboardUi> = _ui

    /** The bottom-screen keys are up (drives their slide in / out). */
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible

    /**
     * The bottom-screen window should exist. Lags [visible] going down: switching between fields
     * hides and shows the keyboard again right away, and the window isn't rebuilt for that.
     */
    private val _panelWanted = MutableStateFlow(false)
    val panelWanted: StateFlow<Boolean> = _panelWanted
    private var panelJob: Job? = null

    private var ime: ImeHost? = null
    private var editor: EditorInfo? = null

    // Selection as the editor last reported it, and the end the touchpad moves while selecting.
    private var selStart = 0
    private var selEnd = 0
    private var anchor = 0
    private var moving = 0

    // Text snapshot taken when a swipe starts (bounds and surrogate pairs), null if unsupported.
    private var text: CharSequence? = null
    private var textOffset = 0

    private var lastShiftTap = 0L

    /** Keys are up on the top screen, drawn by the keyboard service (the Thor is held upright). */
    @Volatile
    private var inlineShown = false

    val isShown: Boolean get() = _visible.value || inlineShown

    fun start() {
        scope.launch {
            daemon.events.filterIsInstance<DaemonEvent.KeyboardKey>().collect { if (it.key == "shift" && isShown) shift() }
        }
        // A daemon (re)start doesn't know the keyboard is up: L1 / R1 are shift only then.
        scope.launch {
            daemon.status.filterIsInstance<DaemonStatus.Connected>().collect { if (isShown) daemon.send(DaemonCommand.KeyboardVisible(true)) }
        }
    }

    // ---- From the keyboard service ------------------------------------------------------------

    fun attach(s: ImeHost) {
        ime = s
    }

    fun detach(s: ImeHost) {
        if (ime === s) ime = null
        setPanel(false, delayMs = 0)
    }

    fun onStartInput(info: EditorInfo) {
        editor = info
        selStart = info.initialSelStart.coerceAtLeast(0)
        selEnd = info.initialSelEnd.coerceAtLeast(0)
    }

    /**
     * Typing starts. [inline]: the keyboard service shows the keys itself, on the top screen (the
     * Thor is held upright); otherwise they go on the bottom screen.
     */
    fun onShow(info: EditorInfo, inline: Boolean) {
        editor = info
        _ui.value = KeyboardUi(layer = KeyboardText.layerFor(info.inputType), enter = KeyboardText.enterFor(info.inputType, info.imeOptions))
        autoShift()
        refreshPreview()
        inlineShown = inline
        if (inline) {
            _visible.value = false
            setPanel(false, delayMs = 0)
        } else {
            showPanel()
        }
        daemon.send(DaemonCommand.KeyboardVisible(true))
    }

    fun onHide() {
        inlineShown = false
        _visible.value = false
        daemon.send(DaemonCommand.KeyboardVisible(false))
        _ui.value = _ui.value.copy(selecting = false)
        setPanel(false, delayMs = 400)
    }

    fun onSelection(start: Int, end: Int) {
        selStart = start
        selEnd = end
        refreshPreview()
    }

    private fun showPanel() {
        _visible.value = true
        // Typing needs the bottom screen: wake it if it was turned off (game mode, menu button).
        val status = daemon.status.value
        if (status is DaemonStatus.Connected && !status.state.value.bottomOn) {
            daemon.send(DaemonCommand.SetBottomPower(true))
            setPanel(true, delayMs = 350)
        } else {
            setPanel(true, delayMs = 0)
        }
    }

    private fun setPanel(wanted: Boolean, delayMs: Long) {
        panelJob?.cancel()
        if (delayMs <= 0) {
            _panelWanted.value = wanted
            return
        }
        panelJob = scope.launch {
            delay(delayMs)
            // Showing waits for the screen; it may have been hidden again meanwhile.
            if (!wanted || _visible.value) _panelWanted.value = wanted
        }
    }

    private val ic: InputConnection? get() = ime?.currentInputConnection

    private fun isPassword(): Boolean = KeyboardText.isPassword(editor?.inputType ?: return false)

    // ---- Keys ---------------------------------------------------------------------------------

    override fun type(s: String) {
        val k = ime ?: return
        val u = _ui.value
        val out = KeyboardText.output(u, s)
        if (out.length == 1) k.sendKeyChar(out[0]) else ic?.commitText(out, 1)
        if (u.shift && !u.caps) _ui.value = _ui.value.copy(shift = false)
        endSelecting()
        autoShift()
    }

    override fun typeExact(s: String) {
        val k = ime ?: return
        if (s.length == 1) k.sendKeyChar(s[0]) else ic?.commitText(s, 1)
        val u = _ui.value
        if (u.shift && !u.caps) _ui.value = u.copy(shift = false)
        endSelecting()
        autoShift()
    }

    override fun space() {
        ime?.sendKeyChar(' ')
        endSelecting()
        autoShift()
    }

    override fun backspace() {
        val k = ime ?: return
        val c = ic
        val raw = editor?.inputType == InputType.TYPE_NULL
        when {
            c == null || raw -> k.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            selStart != selEnd -> c.commitText("", 1)
            else -> if (!c.deleteSurroundingTextInCodePoints(1, 0)) k.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        endSelecting()
        autoShift()
    }

    // ---- Backspace swipe ----------------------------------------------------------------------

    /** Lengths (from the cursor back) of 1, 2, 3, … words; empty if the field can't report text. */
    private var wordLens: List<Int> = emptyList()
    private var wordBase = 0
    private var wordEnd = 0
    private var wordCount = 0

    override fun beginWordDelete() {
        wordLens = emptyList()
        wordCount = 0
        val c = ic ?: return
        val et = try {
            c.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 100_000 }, 0)
        } catch (_: Exception) {
            null
        } ?: return
        val off = et.startOffset
        wordBase = minOf(et.selectionStart, et.selectionEnd) + off
        wordEnd = maxOf(et.selectionStart, et.selectionEnd) + off
        val before = et.text?.subSequence(0, (wordBase - off).coerceIn(0, et.text.length))?.toString() ?: return
        wordLens = KeyboardText.wordsBack(before)
    }

    override fun selectWords(n: Int) {
        val k = n.coerceIn(0, wordLens.size)
        if (k == wordCount) return
        wordCount = k
        val start = wordBase - (if (k == 0) 0 else wordLens[k - 1])
        ic?.setSelection(start, wordEnd)
        haptics.tick()
    }

    override fun endWordDelete(commit: Boolean) {
        if (wordCount > 0) {
            if (commit) ic?.commitText("", 1) else ic?.setSelection(wordBase, wordEnd)
        }
        wordLens = emptyList()
        wordCount = 0
        endSelecting()
        autoShift()
    }

    override fun enter() {
        val k = ime ?: return
        val action = (editor?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (_ui.value.enter != EnterKey.RETURN && action != EditorInfo.IME_ACTION_NONE) {
            ic?.performEditorAction(action)
        } else {
            k.sendKeyChar('\n')
        }
        endSelecting()
    }

    override fun shift() {
        val now = SystemClock.uptimeMillis()
        _ui.value = KeyboardText.shifted(_ui.value, now - lastShiftTap)
        lastShiftTap = now
    }

    override fun layer(l: KeyboardLayer) {
        _ui.value = _ui.value.copy(layer = l, shift = false)
    }

    override fun hideKeyboard() {
        ime?.requestHideSelf(0)
    }

    /** Capital letter at the start of a sentence, when the field asks for it. */
    private fun autoShift() {
        val u = _ui.value
        if (u.caps || u.layer != KeyboardLayer.LETTERS) return
        val type = editor?.inputType ?: return
        val caps = try {
            ic?.getCursorCapsMode(type) ?: 0
        } catch (_: Exception) {
            0
        }
        _ui.value = u.copy(shift = caps != 0)
    }

    // ---- Touchpad / selection -----------------------------------------------------------------

    override fun beginSwipe() {
        val et = try {
            ic?.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 100_000 }, 0)
        } catch (_: Exception) {
            null
        }
        text = et?.text
        textOffset = et?.startOffset ?: 0
        if (et != null) {
            selStart = et.selectionStart + textOffset
            selEnd = et.selectionEnd + textOffset
            if (!_ui.value.selecting) moving = selEnd
        }
    }

    override fun endSwipe() {
        text = null
    }

    override fun move(dir: Int): Boolean {
        val c = ic ?: return false
        val t = text
        val selecting = _ui.value.selecting
        if (t == null) {
            // The field can't report its text: arrow keys (with shift while selecting).
            val code = if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            val meta = if (selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            val now = SystemClock.uptimeMillis()
            c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
            c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
            return true
        }
        val from = when {
            selecting -> moving
            dir < 0 -> selStart
            else -> selEnd
        }
        val to = KeyboardText.step(t, textOffset, from, dir)
        val collapsing = !selecting && selStart != selEnd
        if (to == from && !collapsing) return false
        if (selecting) {
            moving = to
            c.setSelection(anchor, to)
            selStart = minOf(anchor, to)
            selEnd = maxOf(anchor, to)
        } else {
            val p = if (collapsing) from else to
            c.setSelection(p, p)
            selStart = p
            selEnd = p
            moving = p
        }
        haptics.tick()
        return true
    }

    override fun toggleSelect() {
        val on = !_ui.value.selecting
        if (on) {
            beginSwipe()
            endSwipe()
            anchor = selStart
            moving = selEnd
        }
        _ui.value = _ui.value.copy(selecting = on)
    }

    private fun endSelecting() {
        if (_ui.value.selecting) _ui.value = _ui.value.copy(selecting = false)
    }

    override fun copy() {
        val sel = try {
            ic?.getSelectedText(0)
        } catch (_: Exception) {
            null
        }
        if (sel.isNullOrEmpty()) {
            flash("Nothing selected")
            return
        }
        if (isPassword()) {
            flash("Can't copy passwords")
            return
        }
        clipboard()?.setPrimaryClip(ClipData.newPlainText("text", sel))
        endSelecting()
        flash("Copied")
    }

    override fun paste() {
        val clip = clipboard()?.primaryClip
        val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
        if (t.isNullOrEmpty()) {
            flash("Clipboard is empty")
            return
        }
        ic?.commitText(t, 1)
        endSelecting()
        autoShift()
    }

    private fun clipboard(): ClipboardManager? = context.getSystemService(ClipboardManager::class.java)

    private fun flash(msg: String) {
        _ui.value = _ui.value.copy(flash = msg, flashId = SystemClock.uptimeMillis())
    }

    /** Text around the cursor for the touchpad preview (masked in password fields). */
    private fun refreshPreview() {
        val c = ic ?: return
        try {
            val (before, selected, after) = KeyboardText.preview(
                c.getTextBeforeCursor(KeyboardText.PREVIEW_CHARS, 0)?.toString().orEmpty(),
                c.getSelectedText(0)?.toString().orEmpty(),
                c.getTextAfterCursor(KeyboardText.PREVIEW_CHARS, 0)?.toString().orEmpty(),
                isPassword(),
            )
            _ui.value = _ui.value.copy(before = before, selected = selected, after = after)
        } catch (e: Exception) {
            Timber.w(e, "preview")
        }
    }
}
