package com.thorctl.ui.keyboard

import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * A character key. [alternate] is typed by swiping down on the key (it is shown small above the
 * character); [lead] / [trail] widen the key's touch area (not the key) into the half-key gaps of
 * the staggered row.
 */
internal data class Ch(val character: String, val alternate: String?, val lead: Float = 0f, val trail: Float = 0f) : Key {
    override val width: Float get() = 1f + lead + trail
}
