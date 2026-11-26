package com.thorctl.app

import android.app.Application
import com.thorctl.BuildConfig
import com.thorctl.di.AppGraph
import com.thorctl.platform.logging.Logging
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication

/**
 * The persistent system app's process. Android creates its components through Metro's
 * AppComponentFactory, which takes their constructors from [appComponentProviders] (the graph).
 * The graph is created on first use: the daemon's binder hand-off creates [DaemonBinderProvider]
 * while the process is still starting, before [onCreate].
 */
class ThorApp : Application(), MetroApplication {
    private val graph: AppGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }

    override val appComponentProviders: MetroAppComponentProviders get() = graph

    override fun onCreate() {
        super.onCreate()
        Logging.plant(debug = BuildConfig.DEBUG)
        graph.startup.start()
    }
}
