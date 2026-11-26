package com.thorctl.platform

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.thorctl.platform.keyboard.EnterKey
import com.thorctl.platform.keyboard.KeyboardLayer
import com.thorctl.platform.keyboard.KeyboardText
import com.thorctl.platform.keyboard.KeyboardUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyboardTextTest {
    @Test
    fun wordsBackFromTheCursor() {
        assertEquals(listOf(5, 11), KeyboardText.wordsBack("hello world"))
        assertEquals(
            listOf(7, 13),
            KeyboardText.wordsBack("hello world  ")
        ) // spaces go with the word before them
        assertEquals(emptyList(), KeyboardText.wordsBack(""))
        assertEquals(listOf(3), KeyboardText.wordsBack("one two", max = 1))
    }

    @Test
    fun punctuationIsItsOwnWord() {
        assertEquals(listOf(1, 6, 12), KeyboardText.wordsBack("hello world!"))
        assertEquals(listOf(3, 6), KeyboardText.wordsBack("hi ..."))
        assertEquals(listOf(1, 3), KeyboardText.wordsBack("ab."))
    }

    @Test
    fun shiftTapsAndCapsLock() {
        val keyboard = KeyboardUi()
        val shifted = KeyboardText.shifted(keyboard, 10_000)
        assertTrue(shifted.shift)
        val locked = KeyboardText.shifted(shifted, 100)
        assertTrue(locked.caps)
        assertFalse(locked.shift)
        assertEquals(KeyboardUi(), KeyboardText.shifted(locked, 10_000))
        assertFalse(
            KeyboardText.shifted(
                shifted,
                10_000
            ).shift
        ) // a slow second tap just releases it
    }

    @Test
    fun upperCaseOnlyOnTheLetters() {
        assertEquals("A", KeyboardText.output(KeyboardUi(shift = true), "a"))
        assertEquals("A", KeyboardText.output(KeyboardUi(caps = true), "a"))
        assertEquals("a", KeyboardText.output(KeyboardUi(), "a"))
        assertEquals(
            "ä",
            KeyboardText.output(KeyboardUi(layer = KeyboardLayer.SYMBOLS, shift = true), "ä")
        )
    }

    @Test
    fun enterFollowsTheField() {
        val text = InputType.TYPE_CLASS_TEXT
        assertEquals(EnterKey.SEARCH, KeyboardText.enterFor(text, EditorInfo.IME_ACTION_SEARCH))
        assertEquals(EnterKey.SEND, KeyboardText.enterFor(text, EditorInfo.IME_ACTION_SEND))
        assertEquals(
            EnterKey.RETURN,
            KeyboardText.enterFor(
                text or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                EditorInfo.IME_ACTION_SEND
            )
        )
        assertEquals(
            EnterKey.RETURN,
            KeyboardText.enterFor(
                text,
                EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            )
        )
    }

    @Test
    fun numbersStartOnSymbols() {
        assertEquals(KeyboardLayer.SYMBOLS, KeyboardText.layerFor(InputType.TYPE_CLASS_NUMBER))
        assertEquals(KeyboardLayer.SYMBOLS, KeyboardText.layerFor(InputType.TYPE_CLASS_PHONE))
        assertEquals(KeyboardLayer.LETTERS, KeyboardText.layerFor(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun passwords() {
        assertTrue(KeyboardText.isPassword(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(KeyboardText.isPassword(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertFalse(KeyboardText.isPassword(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
    }

    @Test
    fun previewIsOneLineAndMasksPasswords() {
        assertEquals(
            Triple("second", "", "third"),
            KeyboardText.preview("first\nsecond", "", "third\nfourth", password = false)
        )
        assertEquals(
            Triple("•••", "••", "•"),
            KeyboardText.preview("abc", "de", "f", password = true)
        )
        val long = "x".repeat(50)
        assertEquals(
            "x".repeat(18) + " … " + "x".repeat(18),
            KeyboardText.preview("", long, "", password = false).second
        )
    }

    @Test
    fun cursorStepsOverEmoji() {
        val text = "a😀b" // a 😀 b
        assertEquals(1, KeyboardText.step(text, 0, 0, 1))
        assertEquals(3, KeyboardText.step(text, 0, 1, 1)) // not between the surrogates
        assertEquals(1, KeyboardText.step(text, 0, 3, -1))
        assertEquals(4, KeyboardText.step(text, 0, 4, 1)) // end of text
        assertEquals(13, KeyboardText.step(text, 10, 11, 1)) // with an extraction offset
    }
}
