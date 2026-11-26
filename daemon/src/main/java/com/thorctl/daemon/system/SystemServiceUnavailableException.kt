package com.thorctl.daemon.system

/**
 * ServiceManager has no binder for [serviceName] (yet, or while system_server restarts). A
 * [ReflectiveOperationException] like every other failure of a hidden-API call, so callers handle
 * them all with one catch.
 */
class SystemServiceUnavailableException(serviceName: String) : ReflectiveOperationException("service $serviceName not available")
