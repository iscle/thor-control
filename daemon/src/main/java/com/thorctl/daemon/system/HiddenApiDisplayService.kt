package com.thorctl.daemon.system

import android.content.Context
import android.hardware.display.IDisplayManager
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** [DisplayService] over IDisplayManager. */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class HiddenApiDisplayService : DisplayService {
    private val service = HiddenService(Context.DISPLAY_SERVICE, IDisplayManager.Stub::asInterface)

    override fun displays(): List<DisplayInfo> = service.call {
        getDisplayIds(false).toList().mapNotNull { id ->
            val info = getDisplayInfo(id) ?: return@mapNotNull null
            DisplayInfo(id, info.uniqueId.orEmpty(), info.type, info.logicalWidth, info.logicalHeight, info.state)
        }
    }

    override fun brightness(displayId: Int): Float = service.call { getBrightness(displayId) }

    override fun setBrightness(displayId: Int, value: Float) = service.call { setBrightness(displayId, value) }

    override fun requestDisplayPower(displayId: Int, state: Int): Boolean = service.call { requestDisplayPower(displayId, state) }
}
