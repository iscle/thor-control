package com.thorctl.daemon.feature.gamemode

import android.app.ActivityManager.RunningAppProcessInfo
import android.content.pm.ApplicationInfo
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.system.AppProcess
import com.thorctl.daemon.system.PackageService
import com.thorctl.daemon.system.TaskService
import com.thorctl.protocol.Proto
import dev.zacsweers.metro.Inject
import timber.log.Timber

/**
 * Frees memory for the game: closes every other app when the game is picked (user apps are
 * force-stopped, system apps lose their background processes) and later clears background
 * processes that came back. Never touches the game, this app, what is visible on either screen,
 * the keyboard, accessibility services or [ALWAYS_KEEP].
 */
@Inject
class AppCloser(
    private val displays: DisplayController,
    private val packageService: PackageService,
    private val taskService: TaskService,
    private val sys: Sys,
) {
    /** Closes everything but the game and what keeps the device usable. Returns MB freed. */
    fun closeOthers(game: String, running: List<AppProcess>): Int {
        val before = memAvailableMb()
        val keep = keepSet(game)
        val home = packageService.homePackage()
        var stopped = 0
        var trimmed = 0
        val packageNames = running.filter { it.uid >= FIRST_APP_UID }.flatMap { it.packageNames }.toSet() - keep
        for (packageName in packageNames) {
            try {
                val flags = packageService.appFlags(packageName) ?: continue
                if (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_PERSISTENT) != 0 || packageName == home) {
                    // System apps: only their background processes, never the app itself.
                    packageService.killBackground(packageName)
                    trimmed++
                } else {
                    packageService.forceStop(packageName)
                    stopped++
                }
            } catch (e: Throwable) {
                Timber.w(e, "game mode: closing $packageName failed")
            }
        }
        sys.write(COMPACT_MEMORY, "1")
        Thread.sleep(SETTLE_MS) // let the processes die and the kernel settle before measuring
        val freed = (memAvailableMb() - before).coerceAtLeast(0)
        Timber.i("game mode: stopped $stopped apps, trimmed $trimmed system apps, ${freed} MB freed")
        return freed
    }

    /** Clears background processes that came back (or were opened and left) while playing. */
    fun trimCached(game: String, running: List<AppProcess>) {
        val packageNames = running
            .filter { it.uid >= FIRST_APP_UID && it.importance >= RunningAppProcessInfo.IMPORTANCE_CACHED && game !in it.packageNames }
            .flatMap { it.packageNames }
            .toSet() - ALWAYS_KEEP - Proto.PACKAGE - game
        if (packageNames.isEmpty()) return
        for (packageName in packageNames) {
            try {
                packageService.killBackground(packageName)
            } catch (_: Throwable) {
            }
        }
        Timber.i("game mode: cleared ${packageNames.size} background apps (${packageNames.joinToString(limit = 6)})")
    }

    private fun keepSet(game: String): Set<String> {
        val keep = HashSet(ALWAYS_KEEP)
        keep += game
        keep += Proto.PACKAGE
        for (displayId in intArrayOf(displays.topId, displays.bottomId)) {
            if (displayId < 0) continue
            try {
                keep += taskService.visiblePackages(displayId) // e.g. a guide open on the other screen
            } catch (_: Throwable) {
            }
        }
        sys.sh("cmd settings get secure default_input_method")?.let { keep += it.substringBefore('/') }
        sys.sh("cmd settings get secure enabled_accessibility_services")?.split(':')?.forEach { keep += it.substringBefore('/') }
        keep.removeAll { it.isBlank() || it == "null" }
        return keep
    }

    private fun memAvailableMb(): Int =
        sys.read(MEMINFO)?.lineSequence()?.firstOrNull { it.startsWith("MemAvailable:") }
            ?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()?.let { (it / 1024).toInt() } ?: 0

    companion object {
        const val FIRST_APP_UID = 10000
        private const val MEMINFO = "/proc/meminfo"
        private const val COMPACT_MEMORY = "/proc/sys/vm/compact_memory"
        private const val SETTLE_MS = 1200L

        /** Never closed: core system processes and the storage daemon games read from. */
        val ALWAYS_KEEP = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.bluetooth",
            "com.android.networkstack",
            "com.android.providers.media.module",
            "com.google.android.providers.media.module",
        )
    }
}
