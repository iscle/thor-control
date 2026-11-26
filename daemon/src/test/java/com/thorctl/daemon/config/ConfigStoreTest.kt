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
    private val dir = createTempDirectory("thorctl").toFile()
    private val file = File(dir, "nested/config.json")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun defaultsWithoutAFile() {
        val c = ConfigStore(file).apply { load() }
        assertEquals(FanProfile.SMART_ID, c.fanProfile)
        assertEquals(ControllerTarget.FREE, c.controller)
        assertEquals(GamepadTransport.USB, c.gamepadTransport)
        assertEquals(FanProfile.BUILTINS, c.allProfiles())
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
        val c = ConfigStore(file).apply { load() }
        assertEquals(custom.id, c.fanProfile)
        assertEquals(listOf(custom), c.customProfiles)
        assertEquals(custom, c.profile(custom.id))
        assertEquals(ControllerTarget.BOTTOM, c.controller)
        assertEquals(LedState(mode = LedMode.RAINBOW, brightness = 12, sync = false, right = 0xABCDEF), c.leds)
        assertEquals(false, c.keepAccessibility)
        assertEquals(GameOptions(closeApps = false, fan = "max"), c.gameOptions)
        assertEquals(GamepadTransport.BLUETOOTH, c.gamepadTransport)
        assertEquals(false, c.gamepadScreensOff)
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
        val c = ConfigStore(file).apply { load() }
        assertEquals("quiet", c.fanProfile)
        assertEquals(LedMode.BREATHE, c.leds.mode)
        assertEquals(LedState().brightness, c.leds.brightness)
        assertEquals(ControllerTarget.FREE, c.controller)
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
        val c = ConfigStore(file).apply { load() }
        assertEquals("custom-k1", c.fanProfile)
        // The one-point curve can't drive the fan: dropped rather than holding it at 0 %.
        assertEquals(listOf(FanProfile("custom-k1", "Mine", listOf(FanPoint(40f, 10f), FanPoint(70f, 80f)))), c.customProfiles)
        assertEquals(ControllerTarget.BOTTOM, c.controller)
        assertEquals(LedState(mode = LedMode.RAINBOW, left = 255, right = 65280, sync = false, brightness = 90, speed = 1.5f, offWhenAsleep = false), c.leds)
        assertEquals(GameOptions(closeApps = false, gpu = false, fan = "max"), c.gameOptions)
        assertEquals(GamepadTransport.BLUETOOTH, c.gamepadTransport)
        assertEquals(false, c.gamepadScreensOff)
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
        val c = ConfigStore(file).apply { load() }
        assertEquals(FanProfile.SMART_ID, c.fanProfile)
    }
}
