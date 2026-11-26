package com.thorctl.protocol.ipc

/**
 * How the daemon and the app find each other. The daemon runs as root under app_process, so it
 * can't register a system service the app may look up. Instead it calls the app's ContentProvider
 * ([AUTHORITY]) with [METHOD_OFFER] and its [IThorDaemon] binder in the extras under [KEY_BINDER]
 * (as Shizuku does). The provider takes it only from uid 0, and the daemon offers it again until
 * the app has registered. The debug CLI asks the same provider for it with [METHOD_GET].
 */
object ThorIpc {
    const val AUTHORITY = "com.thorctl.ipc"
    const val METHOD_OFFER = "offer_daemon"
    const val METHOD_GET = "get_daemon"
    const val KEY_BINDER = "binder"

    const val UID_ROOT = 0
    const val UID_SYSTEM = 1000
    const val UID_SHELL = 2000
}
