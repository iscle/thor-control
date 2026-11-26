package com.thorctl.daemon.ipc

import android.system.ErrnoException
import android.system.Os
import com.thorctl.protocol.AppContract
import dev.zacsweers.metro.Inject

@Inject
class DataDirAppUidLookup : AppUidLookup {
    override fun appUid(): Int = try {
        Os.stat("/data/data/${AppContract.PACKAGE}").st_uid
    } catch (_: ErrnoException) {
        -1
    }
}
