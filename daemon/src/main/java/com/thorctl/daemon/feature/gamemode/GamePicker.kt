package com.thorctl.daemon.feature.gamemode

import android.content.pm.ApplicationInfo
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.system.PackageService
import com.thorctl.daemon.system.TaskService
import com.thorctl.protocol.AppContract
import dev.zacsweers.metro.Inject

/** Decides which app is "the game": the user-installed app in front on the top screen. */
@Inject
class GamePicker(
    private val displays: DisplayController,
    private val taskService: TaskService,
    private val packageService: PackageService,
) {
    /** The user-installed app in front on the top screen, if any. */
    fun pick(): String? {
        val topDisplay = displays.topId
        if (topDisplay < 0) return null
        val packageName = try {
            taskService.topAppTask(topDisplay)?.packageName
        } catch (_: ReflectiveOperationException) {
            null
        } ?: return null
        if (packageName == AppContract.PACKAGE || packageName == packageService.homePackage()) return null
        val flags = packageService.appFlags(packageName) ?: return null
        // Settings, the browser, ... are not games; wait for a user-installed app.
        return if (flags and ApplicationInfo.FLAG_SYSTEM != 0) null else packageName
    }
}
