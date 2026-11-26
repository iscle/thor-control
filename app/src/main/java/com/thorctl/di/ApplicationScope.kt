package com.thorctl.di

import dev.zacsweers.metro.Qualifier

/** Work that lives as long as the process (overlays, connections), on the main thread. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
