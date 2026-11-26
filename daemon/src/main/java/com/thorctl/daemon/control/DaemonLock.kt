package com.thorctl.daemon.control

import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Serializes changes to the config and the hardware it drives (fan, lights, brightness, routing)
 * between the binder threads, the input thread and the scheduler.
 */
@Inject
@SingleIn(DaemonScope::class)
class DaemonLock
