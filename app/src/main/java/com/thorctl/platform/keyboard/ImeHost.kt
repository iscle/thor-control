package com.thorctl.platform.keyboard

import android.view.inputmethod.InputConnection

/** The keyboard service, as the editor needs it. */
interface ImeHost {
    val currentInputConnection: InputConnection?

    fun sendKeyChar(charCode: Char)

    fun sendDownUpKeyEvents(keyCode: Int)

    fun requestHideSelf(flags: Int)
}
