package com.thorctl.daemon.system

import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** [DisplayService] over IDisplayManager. */
@Inject
@SingleIn(DaemonScope::class)
@ContributesBinding(DaemonScope::class)
class HiddenApiDisplayService : DisplayService {
    private val service = SystemServiceProxy("display", "android.hardware.display.IDisplayManager")

    override fun displays(): List<DisplayInfo> {
        val ids = service.call("getDisplayIds", false) as IntArray
        return ids.toList().mapNotNull { id ->
            val info = service.call("getDisplayInfo", id) ?: return@mapNotNull null
            val infoClass = info.javaClass
            DisplayInfo(
                id = id,
                uniqueId = infoClass.getField("uniqueId").get(info) as? String ?: "",
                type = infoClass.getField("type").getInt(info),
                width = infoClass.getField("logicalWidth").getInt(info),
                height = infoClass.getField("logicalHeight").getInt(info),
                state = infoClass.getField("state").getInt(info),
            )
        }
    }

    override fun brightness(displayId: Int): Float = service.call("getBrightness", displayId) as Float

    override fun setBrightness(displayId: Int, value: Float) {
        service.call("setBrightness", displayId, value)
    }

    override fun requestDisplayPower(displayId: Int, state: Int): Boolean =
        service.call("requestDisplayPower", displayId, state) as Boolean
}
