package com.thorctl.di

import android.app.Application
import com.thorctl.app.AppStartup
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The app's dependency graph, created and held by `ThorApp`. Android creates the entry points in
 * `com.thorctl.app` (activity, services, receiver, provider) through Metro's AppComponentFactory,
 * by constructor injection: each one contributes itself to the maps of
 * [MetroAppComponentProviders] with its `@ActivityKey`, `@ServiceKey`, `@BroadcastReceiverKey` or
 * `@ContentProviderKey`.
 */
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph, MetroAppComponentProviders {
    /** The long-lived parts that start with the process. */
    val startup: AppStartup

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
