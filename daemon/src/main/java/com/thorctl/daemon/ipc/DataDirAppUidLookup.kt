package com.thorctl.daemon.ipc

import android.system.Os
import com.thorctl.protocol.Proto
import dev.zacsweers.metro.Inject

@Inject
class DataDirAppUidLookup : AppUidLookup {
    override fun appUid(): Int = try {
        Os.stat("/data/data/${Proto.PACKAGE}").st_uid
    } catch (_: Exception) {
        -1
    }
}
