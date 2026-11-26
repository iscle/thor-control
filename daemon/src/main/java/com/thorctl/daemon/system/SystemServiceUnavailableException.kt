package com.thorctl.daemon.system

/** ServiceManager has no binder for [serviceName]: not started yet, or system_server is restarting. */
class SystemServiceUnavailableException(serviceName: String) : SystemServiceException("service $serviceName not available")
