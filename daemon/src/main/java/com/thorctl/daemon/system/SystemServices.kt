package com.thorctl.daemon.system

import timber.log.Timber
import android.content.AttributionSource
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.view.InputDevice
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.ipc.ThorIpc
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.lang.reflect.Method

/**
 * Reflection wrappers around hidden system service binder interfaces. The daemon runs as
 * uid 0 under app_process, so permission checks (CONTROL_DISPLAY_BRIGHTNESS,
 * ASSOCIATE_INPUT_DEVICE_TO_DISPLAY, ...) pass and hidden API restrictions do not apply.
 *
 * Signatures verified against the Android 17 (SDK 37) framework on the device.
 */
@Inject
@SingleIn(DaemonScope::class)
class SystemServices {
    private class SystemService(val name: String, val interfaceName: String) {
        private var binder: IBinder? = null
        private var proxy: Any? = null
        private val methods = HashMap<String, Method>()

        @Synchronized
        fun get(): Any? {
            val cachedBinder = binder
            if (cachedBinder != null && cachedBinder.isBinderAlive && proxy != null) return proxy
            val freshBinder = getService(name) ?: return null
            binder = freshBinder
            methods.clear()
            proxy = Class.forName("$interfaceName\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, freshBinder)
            return proxy
        }

        @Synchronized
        fun call(method: String, vararg arguments: Any?): Any? {
            val service = get() ?: throw IllegalStateException("service $name not available")
            val javaMethod = methods.getOrPut(method + arguments.size) {
                service.javaClass.methods.first { it.name == method && it.parameterTypes.size == arguments.size }
            }
            return javaMethod.invoke(service, *arguments)
        }
    }

    private val display = SystemService("display", "android.hardware.display.IDisplayManager")
    private val input = SystemService("input", "android.hardware.input.IInputManager")
    private val power = SystemService("power", "android.os.IPowerManager")
    private val activityTaskManager = SystemService("activity_task", "android.app.IActivityTaskManager")
    private val activityManager = SystemService("activity", "android.app.IActivityManager")

    /**
     * The same service for the binder hand-off only: that call can wait seconds for Android to
     * start the app, and must not hold [activityManager]'s lock (game mode's process calls) meanwhile.
     */
    private val activityManagerForProviders = SystemService("activity", "android.app.IActivityManager")
    private val pm = SystemService("package", "android.content.pm.IPackageManager")

    fun servicesReady(): Boolean =
        getService("display") != null && getService("input") != null &&
            getService("power") != null && getService("activity_task") != null

    // ---- Displays -------------------------------------------------------------------------

    data class DisplayInfo(val id: Int, val uniqueId: String, val type: Int, val width: Int, val height: Int, val state: Int)

    fun displays(): List<DisplayInfo> {
        val ids = display.call("getDisplayIds", false) as IntArray
        return ids.toList().mapNotNull { id ->
            val info = display.call("getDisplayInfo", id) ?: return@mapNotNull null
            val infoClass = info.javaClass
            DisplayInfo(
                id = id,
                uniqueId = infoClass.getField("uniqueId").get(info) as? String ?: "",
                type = infoClass.getField("type").getInt(info),
                width = infoClass.getField("logicalWidth").getInt(info),
                height = infoClass.getField("logicalHeight").getInt(info),
                state = infoClass.getField("state").getInt(info),
            )
        }
    }

    fun getBrightness(displayId: Int): Float = display.call("getBrightness", displayId) as Float

    fun setBrightness(displayId: Int, value: Float) {
        display.call("setBrightness", displayId, value)
    }

    /** state: Display.STATE_OFF (1) to force off, Display.STATE_UNKNOWN (0) to hand control back. */
    fun requestDisplayPower(displayId: Int, state: Int): Boolean =
        display.call("requestDisplayPower", displayId, state) as Boolean

    // ---- Input ----------------------------------------------------------------------------

    fun inputDevices(): List<InputDevice> {
        val ids = input.call("getInputDeviceIds") as IntArray
        return ids.toList().mapNotNull { input.call("getInputDevice", it) as InputDevice? }
    }

    fun associateByDescriptor(descriptor: String, displayUniqueId: String) {
        input.call("addUniqueIdAssociationByDescriptor", descriptor, displayUniqueId)
    }

    fun removeAssociationByDescriptor(descriptor: String) {
        input.call("removeUniqueIdAssociationByDescriptor", descriptor)
    }

    fun setInputDeviceEnabled(deviceId: Int, enabled: Boolean) {
        input.call(if (enabled) "enableInputDevice" else "disableInputDevice", deviceId)
    }

    // ---- Power / tasks --------------------------------------------------------------------

    fun isInteractive(): Boolean = try {
        power.call("isInteractive") as Boolean
    } catch (_: Throwable) {
        true
    }

    /**
     * User activity on [displayId]'s power group, as a touch would be: restarts its screen timeout
     * (and brightens it from dim). Each panel can be its own power group, so call it per display.
     */
    fun userActivity(displayId: Int) {
        power.call("userActivity", displayId, android.os.SystemClock.uptimeMillis(), 0 /* USER_ACTIVITY_EVENT_OTHER */, 0)
    }

    /** Display of the focused task, or -1. */
    fun focusedDisplayId(): Int = try {
        activityTaskManager.call("getFocusedRootTaskInfo")?.let { field(it, "displayId") as? Int } ?: -1
    } catch (_: Throwable) {
        -1
    }

    fun focusTopTask(displayId: Int) {
        activityTaskManager.call("focusTopTask", displayId)
    }

    data class TaskInfo(val taskId: Int, val packageName: String?)

    /** Top-most visible standard (non-home) root task on a display, i.e. the app the user sees. */
    fun topAppTask(displayId: Int): TaskInfo? {
        val list = activityTaskManager.call("getAllRootTaskInfosOnDisplay", displayId) as? List<*> ?: return null
        for (info in list) {
            if (info == null) continue
            val type = field(info, "topActivityType") as? Int ?: continue
            val visible = (field(info, "visible") as? Boolean ?: false) || (field(info, "isVisible") as? Boolean ?: false)
            if (type == ACTIVITY_TYPE_STANDARD && visible) {
                val top = field(info, "topActivity") as? android.content.ComponentName
                    ?: field(info, "baseActivity") as? android.content.ComponentName
                return TaskInfo(field(info, "taskId") as Int, top?.packageName)
            }
        }
        return null
    }

    fun moveTaskToDisplay(taskId: Int, displayId: Int) {
        activityTaskManager.call("moveRootTaskToDisplay", taskId, displayId)
        // A raw reparent skips the "task to front" transition that Launcher tracks; without it
        // Launcher believes it is still visible and the next app-switch toggles the wrong state.
        try {
            val options = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
            activityTaskManager.call("startActivityFromRecents", taskId, options)
        } catch (e: Throwable) {
            Timber.w(e, "bring task $taskId to front failed")
        }
    }

    /** Injects a key press (down + up) as the system would, optionally targeted at one display. */
    fun injectKey(keyCode: Int, displayId: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        for (action in intArrayOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
            val event = android.view.KeyEvent(
                now, now, action, keyCode, 0, 0,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                android.view.KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD,
            )
            if (displayId >= 0) {
                android.view.KeyEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType).invoke(event, displayId)
            }
            input.call("injectInputEvent", event, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */)
        }
    }

    /** Packages of every visible root task on a display (any activity type, home included). */
    fun visiblePackages(displayId: Int): Set<String> {
        val list = activityTaskManager.call("getAllRootTaskInfosOnDisplay", displayId) as? List<*> ?: return emptySet()
        val packages = HashSet<String>()
        for (info in list) {
            if (info == null) continue
            val visible = (field(info, "visible") as? Boolean ?: false) || (field(info, "isVisible") as? Boolean ?: false)
            if (!visible) continue
            ((field(info, "topActivity") ?: field(info, "baseActivity")) as? android.content.ComponentName)?.let { packages += it.packageName }
        }
        return packages
    }

    // ---- Recent tasks ---------------------------------------------------------------------

    /** displayId is -1 for a task that is no longer running (it is restored when opened). */
    data class RecentTask(val taskId: Int, val packageName: String, val displayId: Int, val visible: Boolean, val running: Boolean)

    /** Most recent first, as the system's recents list (home and excluded tasks are left out). */
    fun recentTasks(limit: Int): List<RecentTask> {
        val slice = activityTaskManager.call("getRecentTasks", limit, RECENT_IGNORE_UNAVAILABLE, 0) ?: return emptyList()
        val list = slice.javaClass.getMethod("getList").invoke(slice) as? List<*> ?: return emptyList()
        return list.mapNotNull { info ->
            if (info == null) return@mapNotNull null
            val base = (field(info, "baseIntent") as? android.content.Intent)?.component
            val packageName = ((field(info, "topActivity") ?: field(info, "baseActivity")) as? android.content.ComponentName)?.packageName
                ?: base?.packageName ?: return@mapNotNull null
            RecentTask(
                taskId = field(info, "taskId") as? Int ?: return@mapNotNull null,
                packageName = packageName,
                displayId = field(info, "displayId") as? Int ?: -1,
                visible = field(info, "isVisible") as? Boolean ?: false,
                running = field(info, "isRunning") as? Boolean ?: false,
            )
        }
    }

    /** Brings a recent task to front on [displayId], restoring it if it is no longer running. */
    fun startFromRecents(taskId: Int, displayId: Int) {
        val options = android.app.ActivityOptions.makeBasic()
        if (displayId >= 0) options.launchDisplayId = displayId
        activityTaskManager.call("startActivityFromRecents", taskId, options.toBundle())
    }

    /** Removes the task from recents and closes it, like swiping it away. */
    fun removeTask(taskId: Int): Boolean = activityTaskManager.call("removeTask", taskId) as? Boolean ?: false

    /**
     * Asks the system for a fresh preview of a visible task. With updateCache it is also written
     * to /data/system_ce/0/snapshots/ within ~150 ms, where [snapshotFile] finds it. The returned
     * TaskSnapshot is dropped right away (its graphics buffer is released).
     */
    fun takeSnapshot(taskId: Int) {
        val snapshotManager = activityTaskManager.call("getTaskSnapshotManager") ?: return
        val takeMethod = snapshotManager.javaClass.methods.firstOrNull { it.name == "takeTaskSnapshot" } ?: return
        val arguments: Array<Any?> = when (takeMethod.parameterTypes.size) {
            4 -> arrayOf(taskId, true, false, false)
            3 -> arrayOf(taskId, true, false)
            2 -> arrayOf(taskId, true)
            else -> return
        }
        val snapshot = takeMethod.invoke(snapshotManager, *arguments) ?: return
        try {
            (snapshot.javaClass.getMethod("getHardwareBuffer").invoke(snapshot) as? AutoCloseable)?.close()
        } catch (_: Throwable) {
        }
    }

    /** The persisted low-resolution preview of a task, if the system has one. */
    fun snapshotFile(taskId: Int): java.io.File? {
        val root = java.io.File("/data/system_ce/0/snapshots")
        val directories = listOf(root) + (root.listFiles()?.filter { it.isDirectory } ?: emptyList())
        for (name in listOf("${taskId}_reduced.jpg", "$taskId.jpg")) {
            directories.map { java.io.File(it, name) }.filter { it.isFile }.maxByOrNull { it.lastModified() }?.let { return it }
        }
        return null
    }

    // ---- Processes / packages -------------------------------------------------------------

    data class AppProcess(val pid: Int, val uid: Int, val name: String, val packageNames: List<String>, val importance: Int)

    fun runningApps(): List<AppProcess> {
        val list = activityManager.call("getRunningAppProcesses") as? List<*> ?: return emptyList()
        return list.mapNotNull { process ->
            val info = process as? android.app.ActivityManager.RunningAppProcessInfo ?: return@mapNotNull null
            AppProcess(info.pid, info.uid, info.processName ?: "", info.pkgList?.toList() ?: emptyList(), info.importance)
        }
    }

    fun forceStop(packageName: String) {
        activityManager.call("forceStopPackage", packageName, 0)
    }

    /** Kills the package's processes that are in the background (adj >= service); visible ones survive. */
    fun killBackground(packageName: String) {
        activityManager.call("killBackgroundProcesses", packageName, 0)
    }

    /** ApplicationInfo.flags, or null if the package is unknown. */
    fun appFlags(packageName: String): Int? {
        val packageService = pm.get() ?: return null
        val getInfoMethod = packageService.javaClass.methods.firstOrNull { it.name == "getApplicationInfo" && it.parameterTypes.size == 3 } ?: return null
        val flagsArgument: Any = if (getInfoMethod.parameterTypes[1] == Long::class.javaPrimitiveType) 0L else 0
        val info = getInfoMethod.invoke(packageService, packageName, flagsArgument, 0) as? android.content.pm.ApplicationInfo ?: return null
        return info.flags
    }

    fun homePackage(): String? = try {
        (pm.call("getHomeActivities", ArrayList<android.content.pm.ResolveInfo>()) as? android.content.ComponentName)?.packageName
    } catch (e: Throwable) {
        Timber.w(e, "getHomeActivities failed")
        null
    }

    private fun field(instance: Any, name: String): Any? {
        var currentClass: Class<*>? = instance.javaClass
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)
            } catch (_: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
        return null
    }

    // ---- Handing our binder to the app --------------------------------------------------------

    /**
     * Calls [method] on the app's ContentProvider ([ThorIpc.AUTHORITY]) with [extras], as root,
     * the way `content call` would (IActivityManager.getContentProviderExternal +
     * IContentProvider.call). Starts the app's process if it isn't running. Returns the reply.
     */
    fun callAppProvider(method: String, extras: Bundle): Bundle? {
        val token = Binder()
        val holder = activityManagerForProviders.call("getContentProviderExternal", ThorIpc.AUTHORITY, 0, token, ThorIpc.AUTHORITY)
            ?: throw IllegalStateException("provider ${ThorIpc.AUTHORITY} not found")
        try {
            val provider = field(holder, "provider") ?: throw IllegalStateException("provider ${ThorIpc.AUTHORITY} has no binder")
            val call = provider.javaClass.methods.first { it.name == "call" && it.parameterTypes.size == 5 }
            val source = AttributionSource.Builder(android.os.Process.myUid()).build()
            return call.invoke(provider, source, ThorIpc.AUTHORITY, method, null, extras) as Bundle?
        } finally {
            try {
                activityManagerForProviders.call("removeContentProviderExternalAsUser", ThorIpc.AUTHORITY, token, 0)
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        private const val ACTIVITY_TYPE_STANDARD = 1
        private const val RECENT_IGNORE_UNAVAILABLE = 0x0002

        private fun getService(name: String): IBinder? = try {
            Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, name) as IBinder?
        } catch (_: Throwable) {
            null
        }
    }
}
