package com.thorctl.daemon.feature.gamemode

/**
 * Reads the layer tree in `dumpsys SurfaceFlinger` output. Each display is a direct child of the
 * root (" ├─ Display 1 ..."), and the layers drawn on it are indented below it, each naming the
 * uid that owns it ("uid=10234").
 */
object SurfaceFlingerLayers {
    private val DISPLAY = Regex("""^ [├└]─ Display (\d+) """)
    private val ROOT_CHILD = Regex("""^ [├└]─ """)
    private val UID = Regex("""\buid=(\d+)""")

    /** Whether any layer under display [displayId] in [dump] is owned by one of [uids]. */
    fun hasLayerOwnedBy(dump: String, displayId: Int, uids: Set<Int>): Boolean {
        var inDisplay = false
        for (line in dump.lineSequence()) {
            val displayMatch = DISPLAY.find(line)
            when {
                displayMatch != null -> inDisplay = displayMatch.groupValues[1].toInt() == displayId
                ROOT_CHILD.containsMatchIn(line) || !line.startsWith(" ") -> inDisplay = false
                inDisplay -> UID.find(line)?.let { if (it.groupValues[1].toInt() in uids) return true }
            }
        }
        return false
    }
}
