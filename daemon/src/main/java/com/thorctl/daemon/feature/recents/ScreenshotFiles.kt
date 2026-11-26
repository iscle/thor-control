package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.system.AppTask
import dev.zacsweers.metro.Inject
import java.io.File

/**
 * Where the screen pictures for the Home animation and recents are kept: the app screen of the
 * last Home press, and one home screen per physical display (kept on disk, so it is ready when Home
 * is pressed inside an app).
 */
@Inject
class ScreenshotFiles(private val ids: PhysicalDisplayIds, private val directory: File = DIRECTORY) {
    /** The app on screen when Home last went down. */
    val appScreen: File get() = File(directory, "screen.jpg")

    /** The home screen of [display]. */
    fun home(display: Int): File = File(directory, "home-${ids.of(display)}.jpg")

    /** Where a Home press captures [display]: on the home screen ([origin] null) the capture doubles as a fresh picture of it. */
    fun forPress(display: Int, origin: AppTask?): File = if (origin == null) home(display) else appScreen

    /** The home screen of [display] was pictured less than [HOME_MAX_AGE_MS] ago ([now] is wall-clock time, like file times). */
    fun hasFreshHome(display: Int, now: Long = System.currentTimeMillis()): Boolean {
        val homeImage = home(display)
        return homeImage.exists() && now - homeImage.lastModified() < HOME_MAX_AGE_MS
    }

    companion object {
        val DIRECTORY = File("/data/adb/thorctl")
        const val HOME_MAX_AGE_MS = 60_000L
    }
}
