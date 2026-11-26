package com.thorctl.daemon.feature.recents

/**
 * The switch to the home screen at the end of a Home animation. Each animation gets a sequence
 * number; the switch happens once, for the latest animation only, on whichever comes first: the
 * app saying its overlay covers the screen, or the fallback timer. Used on the recents thread.
 */
class PendingHome {
    private var sequence = 0
    private var pending: Pair<Int, Int>? = null // sequence number to display

    /** A new animation on [display]; returns its sequence number. */
    fun start(display: Int): Int {
        val next = ++sequence
        pending = next to display
        return next
    }

    /** The display to send Home to if [sequence] is the pending animation (once), else null. */
    fun take(sequence: Int): Int? {
        val (pendingSequence, display) = pending ?: return null
        if (pendingSequence != sequence) return null
        pending = null
        return display
    }
}
