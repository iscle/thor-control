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
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        return if (inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE) KeyboardLayer.SYMBOLS else KeyboardLayer.LETTERS
    }

    fun isPassword(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        return inputClass == InputType.TYPE_CLASS_TEXT &&
                (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) ||
                inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    /** Shift key: tap toggles shift, a quick second tap locks caps, a tap with caps on releases both. */
    fun shifted(keyboard: KeyboardUi, sinceLastTapMs: Long): KeyboardUi = when {
        keyboard.caps -> keyboard.copy(caps = false, shift = false)
        keyboard.shift && sinceLastTapMs < SHIFT_DOUBLE_TAP_MS -> keyboard.copy(
            caps = true,
            shift = false
        )

        else -> keyboard.copy(shift = !keyboard.shift)
    }

    /** What a typed key commits: upper case on the letters layer with shift or caps. */
    fun output(keyboard: KeyboardUi, key: String): String =
        if (keyboard.layer == KeyboardLayer.LETTERS && (keyboard.shift || keyboard.caps)) key.uppercase() else key

    /** Lengths (from the cursor back) of 1, 2, 3, … words: spaces, then a run of letters/digits or of punctuation. */
    fun wordsBack(before: String, max: Int = MAX_WORDS): List<Int> {
        val out = ArrayList<Int>()
        var position = before.length
        while (position > 0 && out.size < max) {
            while (position > 0 && before[position - 1].isWhitespace()) position--
            if (position > 0) {
                val word = before[position - 1].isLetterOrDigit()
                while (position > 0 && !before[position - 1].isWhitespace() && before[position - 1].isLetterOrDigit() == word) position--
            }
            out += before.length - position
        }
        return out
    }

    /** The touchpad preview: this line only, passwords masked, long selections shortened. */
    fun preview(
        before: String,
        selected: String,
        after: String,
        password: Boolean
    ): Triple<String, String, String> {
        var shownBefore = before
        var shownSelected = selected
        var shownAfter = after
        if (password) {
            shownBefore = "•".repeat(shownBefore.length)
            shownSelected = "•".repeat(shownSelected.length)
            shownAfter = "•".repeat(shownAfter.length)
        }
        if (shownSelected.length > 40) shownSelected =
            shownSelected.take(18) + " … " + shownSelected.takeLast(18)
        return Triple(
            shownBefore.substringAfterLast('\n'),
            shownSelected,
            shownAfter.substringBefore('\n')
        )
    }

    /**
     * Where the cursor goes one step in [direction] from [from], in a field whose text (from offset
     * [offset]) is [text]: never between the two halves of a surrogate pair.
     */
    fun step(text: CharSequence, offset: Int, from: Int, direction: Int): Int {
        val textEnd = offset + text.length
        var to = (from + direction).coerceIn(0, textEnd)
        val index = to - offset
        if (index in 1 until text.length && Character.isHighSurrogate(text[index - 1]) && Character.isLowSurrogate(
                text[index]
            )
        ) to += direction
        return to.coerceIn(0, textEnd)
    }
}
