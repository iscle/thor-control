package com.thorctl.platform.keyboard

/** What the keys, the touchpad and the tool row do (the keyboard UI calls these). */
interface KeyboardActions {
    fun type(s: String)

    /** Types exactly this (swipe-down characters, accents): no automatic capital. */
    fun typeExact(s: String)

    fun space()

    fun backspace()

    fun enter()

    fun shift()

    fun layer(l: KeyboardLayer)

    fun hideKeyboard()

    /** Backspace swipe: select whole words backwards, delete on release (like Gboard). */
    fun beginWordDelete()

    /** Selects [n] words back from the cursor (0 = back to how it was). */
    fun selectWords(n: Int)

    /** Finger up: delete what is selected; cancelled (or nothing selected): leave the text alone. */
    fun endWordDelete(commit: Boolean)

    fun beginSwipe()

    fun endSwipe()

    /** Moves the cursor (or the selection's moving end) one character; true if it moved. */
    fun move(dir: Int): Boolean

    fun toggleSelect()

    fun copy()

    fun paste()
}
