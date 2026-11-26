package com.thorctl.daemon.system

/** The app's ContentProvider can't be reached: the app isn't installed, or is being replaced. */
class AppProviderUnavailableException(message: String) : Exception(message)
