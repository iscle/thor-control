package com.thorctl.daemon.feature.gamemode

import android.app.ActivityManager.RunningAppProcessInfo
import android.content.pm.ApplicationInfo
import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.hardware.ThorDisplays
import com.thorctl.daemon.system.AppProcess
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakePackageService
import com.thorctl.daemon.system.FakeTaskService
import com.thorctl.protocol.Proto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppCloserTest {
    private val sys = FakeSys()
    private val taskService = FakeTaskService()
    private val packageService = FakePackageService().apply { home = "com.launcher" }
    private val displays = ThorDisplays.controller(FakeDisplayService(listOf(ThorDisplays.TOP, ThorDisplays.BOTTOM)))

    private fun process(uid: Int, vararg packageNames: String, importance: Int = RunningAppProcessInfo.IMPORTANCE_FOREGROUND) =
        AppProcess(pid = uid, uid = uid, name = packageNames.first(), packageNames = packageNames.toList(), importance = importance)

    @Test
    fun closesEverythingButTheGameAndWhatKeepsTheDeviceUsable() {
        val running = listOf(
            process(10100, "com.game"),
            process(10101, "com.user.app"),
            process(10102, "com.android.vending"),
            process(10103, "com.persistent"),
            process(10104, "com.launcher"),
            process(10105, "com.guide"),
            process(10106, "com.keyboard"),
            process(10107, "com.a11y.one", "com.a11y.two"),
            process(10108, Proto.PACKAGE),
            process(10109, "com.android.systemui"),
            process(10110, "com.unknown"),
            process(1000, "com.below.app.uids"),
        )
        packageService.flags += mapOf(
            "com.user.app" to 0,
            "com.android.vending" to ApplicationInfo.FLAG_SYSTEM,
            "com.persistent" to ApplicationInfo.FLAG_PERSISTENT,
            "com.launcher" to 0,
            "com.guide" to 0,
            "com.keyboard" to 0,
            "com.a11y.one" to 0,
            "com.a11y.two" to 0,
            "com.below.app.uids" to 0,
        )
        taskService.visible[ThorDisplays.BOTTOM.id] = setOf("com.guide")
        sys.shell = {
            when (it) {
                "cmd settings get secure default_input_method" -> "com.keyboard/.Ime"
                "cmd settings get secure enabled_accessibility_services" -> "com.a11y.one/.Service:com.a11y.two/.Other"
                else -> null
            }
        }
        sys["/proc/meminfo"] = "MemTotal:       11811188 kB\nMemAvailable:    2097152 kB\n"
        // Memory comes back once compaction is asked for (i.e. after the apps were closed).
        val freeingSys = object : Sys by sys {
            override fun write(path: String, value: String): Boolean {
                if (path == "/proc/sys/vm/compact_memory") sys["/proc/meminfo"] = "MemTotal:       11811188 kB\nMemAvailable:    3145728 kB\n"
                return sys.write(path, value)
            }
        }

        val freedMb = AppCloser(displays, packageService, taskService, freeingSys).closeOthers("com.game", running)

        assertEquals(listOf("com.user.app"), packageService.forceStopped)
        assertEquals(setOf("com.android.vending", "com.persistent", "com.launcher"), packageService.killedInBackground.toSet())
        assertEquals(listOf("/proc/sys/vm/compact_memory" to "1"), sys.writes)
        assertEquals(1024, freedMb)
    }

    @Test
    fun trimsOnlyCachedBackgroundApps() {
        val cached = RunningAppProcessInfo.IMPORTANCE_CACHED
        val running = listOf(
            process(10100, "com.game", importance = cached),
            process(10101, "com.cached", importance = cached),
            process(10102, "com.shared", "com.game", importance = cached),
            process(10103, "com.service", importance = RunningAppProcessInfo.IMPORTANCE_SERVICE),
            process(10104, Proto.PACKAGE, importance = cached),
            process(10105, "com.android.systemui", importance = cached),
            process(1000, "com.system.uid", importance = cached),
        )

        AppCloser(displays, packageService, taskService, sys).trimCached("com.game", running)

        assertEquals(listOf("com.cached"), packageService.killedInBackground)
        assertTrue(packageService.forceStopped.isEmpty())
    }
}
