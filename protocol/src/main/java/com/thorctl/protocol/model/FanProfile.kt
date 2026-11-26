package com.thorctl.protocol.model

data class FanProfile(
    val id: String,
    val name: String,
    /** Empty for the "auto" profile, which hands control back to the kernel thermal governor. */
    val points: List<FanPoint>,
) {
    val isAuto: Boolean get() = id == AUTO_ID
    val isBuiltin: Boolean get() = BUILTINS.any { it.id == id }

    /** Fan duty in percent for the given temperature (linear interpolation, clamped at the ends). */
    fun percentAt(temp: Float): Float {
        if (points.isEmpty()) return 0f
        val p = points.sortedBy { it.temp }
        if (temp <= p.first().temp) return p.first().pct
        if (temp >= p.last().temp) return p.last().pct
        for (i in 0 until p.size - 1) {
            val a = p[i]
            val b = p[i + 1]
            if (temp in a.temp..b.temp) {
                val f = if (b.temp == a.temp) 1f else (temp - a.temp) / (b.temp - a.temp)
                return a.pct + (b.pct - a.pct) * f
            }
        }
        return p.last().pct
    }

    companion object {
        const val AUTO_ID = "auto"
        const val SMART_ID = "smart"

        private fun curve(vararg p: Pair<Int, Int>) = p.map { FanPoint(it.first.toFloat(), it.second.toFloat()) }

        val BUILTINS = listOf(
            FanProfile(AUTO_ID, "Auto", emptyList()),
            FanProfile("quiet", "Quiet", curve(50 to 0, 60 to 18, 70 to 32, 80 to 50, 88 to 72)),
            FanProfile(SMART_ID, "Smart", curve(42 to 0, 50 to 18, 60 to 34, 70 to 56, 80 to 82)),
            FanProfile("sport", "Sport", curve(35 to 18, 45 to 32, 55 to 55, 65 to 80, 75 to 100)),
            FanProfile("max", "Max", curve(30 to 100, 45 to 100, 60 to 100, 75 to 100, 90 to 100)),
        )
    }
}
