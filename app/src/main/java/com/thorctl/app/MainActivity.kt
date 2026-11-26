package com.thorctl.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thorctl.domain.repository.DaemonRepository
import com.thorctl.ui.components.ThorContent
import com.thorctl.ui.status.StatusScreen
import dev.zacsweers.metro.Inject
import timber.log.Timber

/** The app's own screen (status and the menu, on the top screen). */
class MainActivity : ComponentActivity() {
    @Inject lateinit var content: ThorContent

    @Inject lateinit var daemon: DaemonRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Same look as the shade: wallpaper behind, blurred.
        window.setBackgroundBlurRadius(110)
        setContent { content { StatusScreen(onOpenControllerSettings = ::openControllerSettings) } }
    }

    override fun onResume() {
        super.onResume()
        daemon.subscribeStats(true)
    }

    override fun onPause() {
        daemon.subscribeStats(false)
        super.onPause()
    }

    /** LineageOS's AYN settings: stick calibration and button mapping. */
    private fun openControllerSettings() {
        try {
            startActivity(Intent().setComponent(ComponentName("org.lineageos.settings.ayn", "org.lineageos.settings.ayn.AynSettingsActivity")))
        } catch (error: Exception) {
            Timber.w(error, "AYN settings not found")
        }
    }
}
