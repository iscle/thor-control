package com.thorctl.daemon.system

import android.content.Context
import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** [DisplayService] over IDisplayManager. */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class HiddenApiDisplayService : DisplayService {
    private val service = SystemServiceProxy(Context.DISPLAY_SERVICE, "android.hardware.display.IDisplayManager")

    override fun displays(): List<DisplayInfo> {
        return service.getDisplayIds(includeDisabled = false).toList().mapNotNull { id ->
            service.getDisplayInfo(id)?.toDisplayInfo(id)
        }
    }

    override fun brightness(displayId: Int): Float = service.getBrightness(displayId)

    override fun setBrightness(displayId: Int, value: Float) {
        service.setBrightness(displayId, value)
    }

    override fun requestDisplayPower(displayId: Int, state: Int): Boolean =
        service.requestDisplayPower(displayId, state)
}
