package com.thorctl.di

import android.app.Application
import com.thorctl.app.AppStartup
import com.thorctl.app.MainActivity
import com.thorctl.app.ThorKeyboard
import com.thorctl.data.daemon.DaemonConnection
import com.thorctl.platform.accessibility.AccessibilityConnection
import com.thorctl.platform.overlay.OverlayMenu
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The app's dependency graph, created and held by `ThorApp`. Android creates the entry points in
 * `com.thorctl.app` (activity, services, receivers, provider) by name; they reach the graph through
 * `ThorApp.graph`: the activity and the keyboard service by member injection, the others through
 * the accessors below.
 */
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
    /** The long-lived parts that start with the process. */
    val startup: AppStartup

    /** Where the daemon's binder arrives (DaemonBinderProvider). */
    val daemonConnection: DaemonConnection

    val accessibility: AccessibilityConnection

    val menu: OverlayMenu

    fun inject(activity: MainActivity)

    fun inject(service: ThorKeyboard)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
