package com.thorctl.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** The app's own settings (the daemon keeps its own in /data/adb/thorctl/config.json). */
object SettingsKeys {
    val FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
    val GAMEPAD_HOST = stringPreferencesKey("gamepad_host")
}
