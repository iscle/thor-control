package com.thorctl.platform.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** The keyboard's decisions that need no input connection. */
object KeyboardText {
    const val SHIFT_DOUBLE_TAP_MS = 350L
    const val PREVIEW_CHARS = 60
    const val MAX_WORDS = 200

    /** The enter key for a field: its IME action, unless it is multi-line or asks for none. */
    fun enterFor(inputType: Int, imeOptions: Int): EnterKey {
        val multiLine = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 || multiLine) return EnterKey.RETURN
        return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterKey.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterKey.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterKey.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterKey.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterKey.DONE
            else -> EnterKey.RETURN
        }
    }

    /** Numbers and phone numbers start on the symbols layer. */
    fun layerFor(inputType: Int): KeyboardLayer {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        return if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) KeyboardLayer.SYMBOLS else KeyboardLayer.LETTERS
    }

    fun isPassword(inputType: Int): Boolean {
        val v = inputType and InputType.TYPE_MASK_VARIATION
        val cls = inputType and InputType.TYPE_MASK_CLASS
        return cls == InputType.TYPE_CLASS_TEXT &&
            (v == InputType.TYPE_TEXT_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) ||
            cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    /** Shift key: tap toggles shift, a quick second tap locks caps, a tap with caps on releases both. */
    fun shifted(ui: KeyboardUi, sinceLastTapMs: Long): KeyboardUi = when {
        ui.caps -> ui.copy(caps = false, shift = false)
        ui.shift && sinceLastTapMs < SHIFT_DOUBLE_TAP_MS -> ui.copy(caps = true, shift = false)
        else -> ui.copy(shift = !ui.shift)
    }

    /** What a typed key commits: upper case on the letters layer with shift or caps. */
    fun output(ui: KeyboardUi, key: String): String =
        if (ui.layer == KeyboardLayer.LETTERS && (ui.shift || ui.caps)) key.uppercase() else key

    /** Lengths (from the cursor back) of 1, 2, 3, … words: spaces, then a run of letters/digits or of punctuation. */
    fun wordsBack(before: String, max: Int = MAX_WORDS): List<Int> {
        val out = ArrayList<Int>()
        var i = before.length
        while (i > 0 && out.size < max) {
            while (i > 0 && before[i - 1].isWhitespace()) i--
            if (i > 0) {
                val word = before[i - 1].isLetterOrDigit()
                while (i > 0 && !before[i - 1].isWhitespace() && before[i - 1].isLetterOrDigit() == word) i--
            }
            out += before.length - i
        }
        return out
    }

    /** The touchpad preview: this line only, passwords masked, long selections shortened. */
    fun preview(before: String, selected: String, after: String, password: Boolean): Triple<String, String, String> {
        var b = before
        var s = selected
        var a = after
        if (password) {
            b = "•".repeat(b.length)
            s = "•".repeat(s.length)
            a = "•".repeat(a.length)
        }
        if (s.length > 40) s = s.take(18) + " … " + s.takeLast(18)
        return Triple(b.substringAfterLast('\n'), s, a.substringBefore('\n'))
    }

    /**
     * Where the cursor goes one step in [dir] from [from], in a field whose text (from offset
     * [offset]) is [text]: never between the two halves of a surrogate pair.
     */
    fun step(text: CharSequence, offset: Int, from: Int, dir: Int): Int {
        val len = offset + text.length
        var to = (from + dir).coerceIn(0, len)
        val i = to - offset
        if (i in 1 until text.length && Character.isHighSurrogate(text[i - 1]) && Character.isLowSurrogate(text[i])) to += dir
        return to.coerceIn(0, len)
    }
}
