package com.thorctl.daemon.di

import dev.zacsweers.metro.Qualifier

/** /data/adb/thorctl/config.json */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigFile
