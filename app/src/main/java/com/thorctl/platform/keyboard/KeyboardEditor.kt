package com.thorctl.platform.keyboard

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
import com.thorctl.domain.model.DaemonStatus
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.platform.haptics.Haptics
import com.thorctl.protocol.model.DaemonCommand
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
    private val _uiState = MutableStateFlow(KeyboardUi())
    val uiState: StateFlow<KeyboardUi> = _uiState

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
    private var selectionStart = 0
    private var selectionEnd = 0
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

    /** L1 / R1 while the keyboard is up (the daemon forwards it): shift. */
    fun onControllerShift() {
        if (isShown) shift()
    }

    // ---- From the keyboard service ------------------------------------------------------------

    /** The keyboard service is up; [serviceScope] lives as long as it does. */
    fun attach(host: ImeHost, serviceScope: CoroutineScope) {
        ime = host
        // A daemon (re)start doesn't know the keyboard is up: L1 / R1 are shift only then.
        serviceScope.launch {
            daemon.status.filterIsInstance<DaemonStatus.Connected>()
                .collect { if (isShown) daemon.send(DaemonCommand.KeyboardVisible(true)) }
        }
    }

    fun detach(host: ImeHost) {
        if (ime === host) ime = null
        setPanel(false, delayMs = 0)
    }

    fun onStartInput(info: EditorInfo) {
        editor = info
        selectionStart = info.initialSelStart.coerceAtLeast(0)
        selectionEnd = info.initialSelEnd.coerceAtLeast(0)
    }

    /**
     * Typing starts. [inline]: the keyboard service shows the keys itself, on the top screen (the
     * Thor is held upright); otherwise they go on the bottom screen.
     */
    fun onShow(info: EditorInfo, inline: Boolean) {
        editor = info
        _uiState.value = KeyboardUi(
            layer = KeyboardText.layerFor(info.inputType),
            enter = KeyboardText.enterFor(info.inputType, info.imeOptions)
        )
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
        _uiState.value = _uiState.value.copy(selecting = false)
        setPanel(false, delayMs = 400)
    }

    fun onSelection(start: Int, end: Int) {
        selectionStart = start
        selectionEnd = end
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

    private val inputConnection: InputConnection? get() = ime?.currentInputConnection

    private fun isPassword(): Boolean = KeyboardText.isPassword(editor?.inputType ?: return false)

    // ---- Keys ---------------------------------------------------------------------------------

    override fun type(text: String) {
        val imeHost = ime ?: return
        val keyboard = _uiState.value
        val out = KeyboardText.output(keyboard, text)
        if (out.length == 1) imeHost.sendKeyChar(out[0]) else inputConnection?.commitText(out, 1)
        if (keyboard.shift && !keyboard.caps) _uiState.value = _uiState.value.copy(shift = false)
        endSelecting()
        autoShift()
    }

    override fun typeExact(text: String) {
        val imeHost = ime ?: return
        if (text.length == 1) imeHost.sendKeyChar(text[0]) else inputConnection?.commitText(text, 1)
        val keyboard = _uiState.value
        if (keyboard.shift && !keyboard.caps) _uiState.value = keyboard.copy(shift = false)
        endSelecting()
        autoShift()
    }

    override fun space() {
        ime?.sendKeyChar(' ')
        endSelecting()
        autoShift()
    }

    override fun backspace() {
        val imeHost = ime ?: return
        val connection = inputConnection
        val raw = editor?.inputType == InputType.TYPE_NULL
        when {
            connection == null || raw -> imeHost.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            selectionStart != selectionEnd -> connection.commitText("", 1)
            else -> if (!connection.deleteSurroundingTextInCodePoints(
                    1,
                    0
                )
            ) imeHost.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        endSelecting()
        autoShift()
    }

    // ---- Backspace swipe ----------------------------------------------------------------------

    /** Lengths (from the cursor back) of 1, 2, 3, … words; empty if the field can't report text. */
    private var wordLengths: List<Int> = emptyList()
    private var wordBase = 0
    private var wordEnd = 0
    private var wordCount = 0

    override fun beginWordDelete() {
        wordLengths = emptyList()
        wordCount = 0
        val connection = inputConnection ?: return
        val extracted = try {
            connection.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 100_000 }, 0)
        } catch (_: Exception) {
            null
        } ?: return
        val offset = extracted.startOffset
        wordBase = minOf(extracted.selectionStart, extracted.selectionEnd) + offset
        wordEnd = maxOf(extracted.selectionStart, extracted.selectionEnd) + offset
        val before =
            extracted.text?.subSequence(0, (wordBase - offset).coerceIn(0, extracted.text.length))
                ?.toString() ?: return
        wordLengths = KeyboardText.wordsBack(before)
    }

    override fun selectWords(count: Int) {
        val words = count.coerceIn(0, wordLengths.size)
        if (words == wordCount) return
        wordCount = words
        val start = wordBase - (if (words == 0) 0 else wordLengths[words - 1])
        inputConnection?.setSelection(start, wordEnd)
        haptics.tick()
    }

    override fun endWordDelete(commit: Boolean) {
        if (wordCount > 0) {
            if (commit) inputConnection?.commitText("", 1) else inputConnection?.setSelection(
                wordBase,
                wordEnd
            )
        }
        wordLengths = emptyList()
        wordCount = 0
        endSelecting()
        autoShift()
    }

    override fun enter() {
        val imeHost = ime ?: return
        val action = (editor?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (_uiState.value.enter != EnterKey.RETURN && action != EditorInfo.IME_ACTION_NONE) {
            inputConnection?.performEditorAction(action)
        } else {
            imeHost.sendKeyChar('\n')
        }
        endSelecting()
    }

    override fun shift() {
        val now = SystemClock.uptimeMillis()
        _uiState.value = KeyboardText.shifted(_uiState.value, now - lastShiftTap)
        lastShiftTap = now
    }

    override fun layer(target: KeyboardLayer) {
        _uiState.value = _uiState.value.copy(layer = target, shift = false)
    }

    override fun hideKeyboard() {
        ime?.requestHideSelf(0)
    }

    /** Capital letter at the start of a sentence, when the field asks for it. */
    private fun autoShift() {
        val keyboard = _uiState.value
        if (keyboard.caps || keyboard.layer != KeyboardLayer.LETTERS) return
        val type = editor?.inputType ?: return
        val caps = try {
            inputConnection?.getCursorCapsMode(type) ?: 0
        } catch (_: Exception) {
            0
        }
        _uiState.value = keyboard.copy(shift = caps != 0)
    }

    // ---- Touchpad / selection -----------------------------------------------------------------

    override fun beginSwipe() {
        val extracted = try {
            inputConnection?.getExtractedText(ExtractedTextRequest().apply {
                hintMaxChars = 100_000
            }, 0)
        } catch (_: Exception) {
            null
        }
        text = extracted?.text
        textOffset = extracted?.startOffset ?: 0
        if (extracted != null) {
            selectionStart = extracted.selectionStart + textOffset
            selectionEnd = extracted.selectionEnd + textOffset
            if (!_uiState.value.selecting) moving = selectionEnd
        }
    }

    override fun endSwipe() {
        text = null
    }

    override fun move(direction: Int): Boolean {
        val connection = inputConnection ?: return false
        val snapshot = text
        val selecting = _uiState.value.selecting
        if (snapshot == null) {
            // The field can't report its text: arrow keys (with shift while selecting).
            val keyCode =
                if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            val metaState =
                if (selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            val now = SystemClock.uptimeMillis()
            connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
            connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
            return true
        }
        val from = when {
            selecting -> moving
            direction < 0 -> selectionStart
            else -> selectionEnd
        }
        val to = KeyboardText.step(snapshot, textOffset, from, direction)
        val collapsing = !selecting && selectionStart != selectionEnd
        if (to == from && !collapsing) return false
        if (selecting) {
            moving = to
            connection.setSelection(anchor, to)
            selectionStart = minOf(anchor, to)
            selectionEnd = maxOf(anchor, to)
        } else {
            val cursor = if (collapsing) from else to
            connection.setSelection(cursor, cursor)
            selectionStart = cursor
            selectionEnd = cursor
            moving = cursor
        }
        haptics.tick()
        return true
    }

    override fun toggleSelect() {
        val on = !_uiState.value.selecting
        if (on) {
            beginSwipe()
            endSwipe()
            anchor = selectionStart
            moving = selectionEnd
        }
        _uiState.value = _uiState.value.copy(selecting = on)
    }

    private fun endSelecting() {
        if (_uiState.value.selecting) _uiState.value = _uiState.value.copy(selecting = false)
    }

    override fun copy() {
        val selection = try {
            inputConnection?.getSelectedText(0)
        } catch (_: Exception) {
            null
        }
        if (selection.isNullOrEmpty()) {
            flash("Nothing selected")
            return
        }
        if (isPassword()) {
            flash("Can't copy passwords")
            return
        }
        clipboard()?.setPrimaryClip(ClipData.newPlainText("text", selection))
        endSelecting()
        flash("Copied")
    }

    override fun paste() {
        val clip = clipboard()?.primaryClip
        val pasted = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)
            ?.toString() else null
        if (pasted.isNullOrEmpty()) {
            flash("Clipboard is empty")
            return
        }
        inputConnection?.commitText(pasted, 1)
        endSelecting()
        autoShift()
    }

    private fun clipboard(): ClipboardManager? =
        context.getSystemService(ClipboardManager::class.java)

    private fun flash(message: String) {
        _uiState.value = _uiState.value.copy(flash = message, flashId = SystemClock.uptimeMillis())
    }

    /** Text around the cursor for the touchpad preview (masked in password fields). */
    private fun refreshPreview() {
        val connection = inputConnection ?: return
        try {
            val (before, selected, after) = KeyboardText.preview(
                connection.getTextBeforeCursor(KeyboardText.PREVIEW_CHARS, 0)?.toString().orEmpty(),
                connection.getSelectedText(0)?.toString().orEmpty(),
                connection.getTextAfterCursor(KeyboardText.PREVIEW_CHARS, 0)?.toString().orEmpty(),
                isPassword(),
            )
            _uiState.value =
                _uiState.value.copy(before = before, selected = selected, after = after)
        } catch (e: Exception) {
            Timber.w(e, "preview")
        }
    }
}
