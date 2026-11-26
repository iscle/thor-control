package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.hardware.DisplayController
import dev.zacsweers.metro.Inject

/**
 * Turns a logical display id into its physical one (the number after "local:" in the display's
 * unique id): what screencap's -d takes, and stable enough to name a file after.
 */
@Inject
class PhysicalDisplayIds(private val displays: DisplayController) {
    /** The physical id of [display]; empty while the screens are not known yet. */
    fun of(display: Int): String =
        (if (display == displays.bottomId) displays.bottomUniqueId else displays.topUniqueId).substringAfter("local:")
}
