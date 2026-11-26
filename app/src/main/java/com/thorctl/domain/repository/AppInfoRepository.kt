package com.thorctl.domain.repository

/** Installed apps, by package name. */
interface AppInfoRepository {
    /** The app's launcher label; the package name if it has none, "" for "". */
    fun label(pkg: String): String
}
