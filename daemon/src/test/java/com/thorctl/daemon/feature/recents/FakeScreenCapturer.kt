package com.thorctl.daemon.feature.recents

import java.io.File

/** Writes a picture-sized file for every capture while [works]. */
class FakeScreenCapturer : ScreenCapturer {
    var works = true

    /** (display, file name) of every capture asked for, in order. */
    val captures = mutableListOf<Pair<Int, String>>()

    override fun capture(display: Int, file: File): Boolean {
        captures += display to file.name
        if (!works) return false
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(2000) { display.toByte() })
        return true
    }
}
