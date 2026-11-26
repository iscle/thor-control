package com.thorctl.di

import dev.zacsweers.metro.Qualifier

/** DataStore's own scope, off the main thread. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class StorageScope
