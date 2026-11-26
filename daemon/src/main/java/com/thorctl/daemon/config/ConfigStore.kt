package com.thorctl.daemon.config

import com.thorctl.daemon.control.FanProfiles
import com.thorctl.daemon.di.ConfigFile
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.model.ControllerTarget
import com.thorctl.protocol.model.FanProfile
import com.thorctl.protocol.model.GameOptions
import com.thorctl.protocol.model.GamepadTransport
import com.thorctl.protocol.model.LedState
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/**
 * Persistent daemon settings in /data/adb/thorctl/config.json (root-only directory), as
 * [StoredConfig]. Not thread-safe by itself: callers hold the [com.thorctl.daemon.control.DaemonLock].
 */
@Inject
@SingleIn(DaemonScope::class)
class ConfigStore(@ConfigFile private val file: File) {
    var fanProfile: String = FanProfile.SMART_ID
    val customProfiles = ArrayList<FanProfile>()
    var controller: ControllerTarget = ControllerTarget.FREE
    var leds: LedState = LedState()
    var keepAccessibility: Boolean = true
    var gameOptions: GameOptions = GameOptions()

    // Gamepad mode itself always starts off; how it connects is remembered.
    var gamepadTransport: GamepadTransport = GamepadTransport.USB
    var gamepadScreensOff: Boolean = true

    fun load() {
        try {
            if (!file.exists()) return
            val c = FORMAT.decodeFromString(StoredConfig.serializer(), file.readText())
            fanProfile = c.fanProfile
            customProfiles.clear()
            // A curve the fan can't run (too few points) would hold the fan at 0 %: drop it.
            customProfiles += c.customProfiles.mapNotNull { FanProfiles.sanitize(it.toModel())?.takeIf { p -> !p.isBuiltin } }
            controller = ControllerTarget.fromWire(c.controller) ?: ControllerTarget.FREE
            leds = c.leds.toModel()
            keepAccessibility = c.keepAccessibility
            gameOptions = c.gameOptions.toModel()
            gamepadTransport = GamepadTransport.fromWire(c.gamepad.transport) ?: GamepadTransport.USB
            gamepadScreensOff = c.gamepad.screensOff
            if (profile(fanProfile) == null) fanProfile = FanProfile.SMART_ID
            Timber.i("config loaded: fan=$fanProfile controller=${controller.wire} custom=${customProfiles.size}")
        } catch (e: Exception) {
            Timber.w(e, "config load failed, using defaults")
        }
    }

    @Synchronized
    fun save() {
        try {
            file.parentFile?.mkdirs()
            val c = StoredConfig(
                fanProfile = fanProfile,
                customProfiles = customProfiles.map(StoredFanProfile::of),
                controller = controller.wire,
                leds = StoredLeds.of(leds),
                keepAccessibility = keepAccessibility,
                gameOptions = StoredGameOptions.of(gameOptions),
                gamepad = StoredGamepad(gamepadTransport.wire, gamepadScreensOff),
            )
            // Written next to it and renamed over it: a crash mid-write never leaves half a file.
            val tmp = File(file.path + ".tmp")
            tmp.writeText(FORMAT.encodeToString(StoredConfig.serializer(), c))
            if (!tmp.renameTo(file)) Timber.w("config save: rename failed")
        } catch (e: Exception) {
            Timber.w(e, "config save failed")
        }
    }

    fun allProfiles(): List<FanProfile> = FanProfile.BUILTINS + customProfiles

    fun profile(id: String): FanProfile? = allProfiles().firstOrNull { it.id == id }

    private companion object {
        /** Readable on the device (`su -c cat`), and tolerant of keys from other versions. */
        @OptIn(ExperimentalSerializationApi::class)
        val FORMAT = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }
}
