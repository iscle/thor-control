package com.thorctl.daemon.system

/**
 * A call to a system service failed: the service isn't there (yet, or while system_server
 * restarts), the call failed over binder, or the service refused it. Every method of the
 * [DisplayService], [InputService], [PowerService], [TaskService], [PackageService] and
 * [AppProviderClient] interfaces fails with this and nothing else.
 */
open class SystemServiceException(message: String, cause: Throwable? = null) : Exception(message, cause)
