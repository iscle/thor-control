package com.thorctl.platform.logging

import timber.log.Timber

object Logging {
    /** Plants the build's tree once per process. */
    fun plant(debug: Boolean) {
        if (Timber.treeCount > 0) return
        Timber.plant(if (debug) Timber.DebugTree() else ReleaseTree())
    }
}
