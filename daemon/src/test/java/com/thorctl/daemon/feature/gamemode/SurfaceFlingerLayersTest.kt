package com.thorctl.daemon.feature.gamemode

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceFlingerLayersTest {
    @Test
    fun findsLayersUnderTheRightDisplay() {
        assertTrue(SurfaceFlingerLayers.hasLayerOwnedBy(SAMPLE, displayId = 0, uids = setOf(10234)))
        assertFalse(SurfaceFlingerLayers.hasLayerOwnedBy(SAMPLE, displayId = 1, uids = setOf(10234)))
        assertTrue(SurfaceFlingerLayers.hasLayerOwnedBy(SAMPLE, displayId = 1, uids = setOf(10300)))
    }

    @Test
    fun ignoresOffscreenLayersAndOtherUids() {
        assertFalse(SurfaceFlingerLayers.hasLayerOwnedBy(SAMPLE, displayId = 1, uids = setOf(10777)))
        assertFalse(SurfaceFlingerLayers.hasLayerOwnedBy(SAMPLE, displayId = 0, uids = setOf(1023, 102345)))
        assertFalse(SurfaceFlingerLayers.hasLayerOwnedBy(SAMPLE, displayId = 2, uids = setOf(10234, 10300)))
    }

    @Test
    fun emptyDumpHasNoLayers() {
        assertFalse(SurfaceFlingerLayers.hasLayerOwnedBy("", displayId = 0, uids = setOf(10234)))
    }

    private companion object {
        /** Shape of the "Layer Hierarchy" section: displays are direct children of the root, one space in. */
        val SAMPLE = listOf(
            "Display 4630946976625403009 (active) HWC layers:",
            "Layer Hierarchy",
            " ROOT",
            " ├─ Display 0 displayName=Built-in Screen",
            " │  ├─ 12 WindowedMagnification:0:31#3 uid=1000",
            " │  │  └─ 45 com.example.game/com.example.game.MainActivity#120 uid=10234",
            " │  └─ 13 StatusBar#7 uid=10050",
            " └─ Display 1 displayName=Built-in Screen 2",
            "    ├─ 60 com.launcher/.Home#200 uid=10100",
            "    └─ 61 Presentation#210 uid=10300",
            "Offscreen Hierarchy",
            " ├─ 99 Surface(name=leftover)#5 uid=10777",
        ).joinToString("\n")
    }
}
