package com.thorctl.daemon.control

import com.thorctl.protocol.model.FanPoint
import com.thorctl.protocol.model.FanProfile

/** Rules for custom fan curves coming from a client. */
object FanProfiles {
    const val MIN_POINTS = 2
    const val MAX_POINTS = 8
    const val MAX_NAME = 24

    /** A curve the fan can safely run: 2–8 points within 20–100 °C and 0–100 %, sorted; null if unusable. */
    fun sanitize(p: FanProfile): FanProfile? {
        if (p.id.isBlank() || p.points.size < MIN_POINTS || p.points.size > MAX_POINTS) return null
        val pts = p.points
            .map { FanPoint(it.temp.coerceIn(20f, 100f), it.pct.coerceIn(0f, 100f)) }
            .sortedBy { it.temp }
        return p.copy(name = p.name.take(MAX_NAME).ifBlank { "Custom" }, points = pts)
    }
}
