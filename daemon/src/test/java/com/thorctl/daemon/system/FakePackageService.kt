package com.thorctl.daemon.system

class FakePackageService : PackageService {
    var running: List<AppProcess> = emptyList()
    val flags = HashMap<String, Int>()
    var home: String? = null
    val forceStopped = mutableListOf<String>()
    val killedInBackground = mutableListOf<String>()

    override fun runningApps(): List<AppProcess> = running

    override fun forceStop(packageName: String) {
        forceStopped += packageName
    }

    override fun killBackground(packageName: String) {
        killedInBackground += packageName
    }

    override fun appFlags(packageName: String): Int? = flags[packageName]

    override fun homePackage(): String? = home
}
