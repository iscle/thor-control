package com.thorctl.daemon.feature.recents

import java.io.File

/** Takes a JPEG picture of what a display shows right now. */
interface ScreenCapturer {
    /** Captures [display] into [file], replacing it only with a complete picture. False if none was taken. */
    fun capture(display: Int, file: File): Boolean
}
