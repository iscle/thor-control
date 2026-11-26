package com.thorctl.di

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.thorctl.domain.repository.Clock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Things Android or the platform provide. */
@ContributesTo(AppScope::class)
@BindingContainer
object AppProviders {
    @Provides
    fun context(application: Application): Context = application

    @Provides
    @SingleIn(AppScope::class)
    @ApplicationScope
    fun applicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @SingleIn(AppScope::class)
    @StorageScope
    fun storageScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    fun clock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    fun appFlags(application: Application): AppFlags = AppFlags(
        system = application.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        persistent = application.applicationInfo.flags and ApplicationInfo.FLAG_PERSISTENT != 0,
    )
}
