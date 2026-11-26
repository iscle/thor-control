package com.thorctl.daemon.config

import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.FanPoint
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.GameOptions
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.protocol.model.LedMode
import com.thorctl.protocol.model.LedState
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigStoreTest {
    private val directory = createTempDirectory("thorctl").toFile()
    private val file = File(directory, "nested/config.json")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun defaultsWithoutAFile() {
        val store = ConfigStore(file).apply { load() }
        assertEquals(FanProfile.SMART_ID, store.fanProfile)
        assertEquals(ControllerTarget.FREE, store.controller)
        assertEquals(GamepadTransport.USB, store.gamepadTransport)
        assertEquals(FanProfile.BUILTINS, store.allProfiles())
    }

    @Test
    fun savesAndLoadsEverything() {
        val custom = FanProfile("custom-1", "Mine", listOf(FanPoint(40f, 5f), FanPoint(75f, 90f)))
        ConfigStore(file).apply {
            fanProfile = custom.id
            customProfiles += custom
            controller = ControllerTarget.BOTTOM
            leds = LedState(mode = LedMode.RAINBOW, brightness = 12, sync = false, right = 0xABCDEF)
            keepAccessibility = false
            gameOptions = GameOptions(closeApps = false, fan = "max")
            gamepadTransport = GamepadTransport.BLUETOOTH
            gamepadScreensOff = false
            save()
        }
        val store = ConfigStore(file).apply { load() }
        assertEquals(custom.id, store.fanProfile)
        assertEquals(listOf(custom), store.customProfiles)
        assertEquals(custom, store.profile(custom.id))
        assertEquals(ControllerTarget.BOTTOM, store.controller)
        assertEquals(LedState(mode = LedMode.RAINBOW, brightness = 12, sync = false, right = 0xABCDEF), store.leds)
        assertEquals(false, store.keepAccessibility)
        assertEquals(GameOptions(closeApps = false, fan = "max"), store.gameOptions)
        assertEquals(GamepadTransport.BLUETOOTH, store.gamepadTransport)
        assertEquals(false, store.gamepadScreensOff)
    }

    @Test
    fun valuesThisVersionDoesNotKnowFallBackToDefaults() {
        ConfigStore(file).apply {
            controller = ControllerTarget.TOP
            save()
        }
        // A newer daemon's controller target and a key this version doesn't know.
        file.writeText(file.readText().replace("\"top\"", "\"mid\"").replaceFirst("{", "{\"futureKey\": 1,"))
        assertEquals(ControllerTarget.FREE, ConfigStore(file).apply { load() }.controller)
    }

    @Test
    fun readsAHandWrittenFileWithOnlySomeKeys() {
        file.parentFile!!.mkdirs()
        file.writeText("""{"fanProfile": "quiet", "leds": {"mode": "breathe"}}""")
        val store = ConfigStore(file).apply { load() }
        assertEquals("quiet", store.fanProfile)
        assertEquals(LedMode.BREATHE, store.leds.mode)
        assertEquals(LedState().brightness, store.leds.brightness)
        assertEquals(ControllerTarget.FREE, store.controller)
    }

    /** What 2.2 wrote with org.json: custom curves as [[temp, percent]] pairs, gamepad settings nested. */
    @Test
    fun readsTheFileEarlierVersionsWrote() {
        file.parentFile!!.mkdirs()
        file.writeText(
            """
            {
              "fanProfile": "custom-k1",
              "customProfiles": [
                {"id": "custom-k1", "name": "Mine", "points": [[40.0, 10.0], [70.0, 80.0]]},
                {"id": "custom-k2", "name": "Broken", "points": [[40.0, 10.0]]}
              ],
              "controller": "bottom",
              "leds": {"enabled": true, "mode": "rainbow", "left": 255, "right": 65280, "sync": false, "brightness": 90, "speed": 1.5, "offWhenAsleep": false},
              "keepAccessibility": false,
              "gameOptions": {"closeApps": false, "cpu": true, "gpu": false, "fan": "max", "bottomOff": true},
              "gamepad": {"transport": "bt", "screensOff": false}
            }
            """.trimIndent(),
        )
        val store = ConfigStore(file).apply { load() }
        assertEquals("custom-k1", store.fanProfile)
        // The one-point curve can't drive the fan: dropped rather than holding it at 0 %.
        assertEquals(listOf(FanProfile("custom-k1", "Mine", listOf(FanPoint(40f, 10f), FanPoint(70f, 80f)))), store.customProfiles)
        assertEquals(ControllerTarget.BOTTOM, store.controller)
        assertEquals(LedState(mode = LedMode.RAINBOW, left = 255, right = 65280, sync = false, brightness = 90, speed = 1.5f, offWhenAsleep = false), store.leds)
        assertEquals(GameOptions(closeApps = false, gpu = false, fan = "max"), store.gameOptions)
        assertEquals(GamepadTransport.BLUETOOTH, store.gamepadTransport)
        assertEquals(false, store.gamepadScreensOff)
    }

    @Test
    fun aMissingActiveProfileFallsBackToSmart() {
        file.parentFile!!.mkdirs()
        file.writeText("""{"fanProfile": "custom-gone"}""")
        assertEquals(FanProfile.SMART_ID, ConfigStore(file).apply { load() }.fanProfile)
    }

    @Test
    fun aBrokenFileFallsBackToDefaults() {
        file.parentFile!!.mkdirs()
        file.writeText("{not json")
        val store = ConfigStore(file).apply { load() }
        assertEquals(FanProfile.SMART_ID, store.fanProfile)
    }
}
