package com.thorctl.daemon.system

/** Installed packages and their running processes (package manager and activity manager). */
interface PackageService {
    fun runningApps(): List<AppProcess>

    fun forceStop(packageName: String)

    /** Kills the package's processes that are in the background (adj >= service); visible ones survive. */
    fun killBackground(packageName: String)

    /** ApplicationInfo.flags, or null if the package is unknown. */
    fun appFlags(packageName: String): Int?

    /** The default launcher's package, or null. */
    fun homePackage(): String?
}
