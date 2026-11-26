package com.thorctl.app

import android.app.Application
import com.thorctl.BuildConfig
import com.thorctl.di.AppGraph
import com.thorctl.platform.logging.Logging
import dev.zacsweers.metro.createGraphFactory

/**
 * The persistent system app's process. The graph is created on first use: the daemon may call
 * [DaemonBinderProvider] while the process is still starting, before [onCreate].
 */
class ThorApp : Application() {
    val graph: AppGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }

    override fun onCreate() {
        super.onCreate()
        Logging.plant(debug = BuildConfig.DEBUG)
        graph.startup.start()
    }
}
