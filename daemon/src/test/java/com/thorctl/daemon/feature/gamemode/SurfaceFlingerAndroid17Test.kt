package com.thorctl.daemon.feature.gamemode

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The layer tree as Android 17 prints it on the Thor (captured with `adb shell dumpsys
 * SurfaceFlinger`): if a release changes the format, this fails instead of game mode quietly
 * turning off a bottom screen the game draws on.
 */
class SurfaceFlingerAndroid17Test {
    private val dump = checkNotNull(javaClass.classLoader.getResource("surfaceflinger-android17.txt")).readText()

    @Test
    fun findsLayersUnderTheDisplayTheyAreOn() {
        assertTrue(SurfaceFlingerLayers.hasLayerOwnedBy(dump, displayId = 0, uids = setOf(10170)))
        assertTrue(SurfaceFlingerLayers.hasLayerOwnedBy(dump, displayId = 1, uids = setOf(10124)))
    }

    @Test
    fun aLayerOnlyOnTheOtherDisplayDoesNotCount() {
        assertFalse(SurfaceFlingerLayers.hasLayerOwnedBy(dump, displayId = 1, uids = setOf(10170)))
        assertFalse(SurfaceFlingerLayers.hasLayerOwnedBy(dump, displayId = 1, uids = setOf(99999)))
    }
}
