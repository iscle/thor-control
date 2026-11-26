package com.thorctl.daemon.di

/**
 * The root daemon's process. A scope of its own (not the app's AppScope): the daemon is a
 * different program that only happens to ship in the same APK.
 */
abstract class DaemonScope private constructor()
