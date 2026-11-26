package com.thorctl.daemon.system

import android.content.Context
import android.hardware.display.DisplayTopology
import android.hardware.display.IDisplayManager
import android.hardware.display.IDisplayManagerCallback
import android.os.IBinder
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

    @Volatile
    private var listener: DisplayChangeListener? = null

    /** The display manager's binder the callback is registered with. */
    private var watchedOn: IBinder? = null

    private val callback = object : IDisplayManagerCallback.Stub() {
        override fun onDisplayEvent(displayId: Int, event: Int) {
            val change = when (event) {
                EVENT_DISPLAY_ADDED -> DisplayChange.ADDED
                EVENT_DISPLAY_REMOVED -> DisplayChange.REMOVED
                EVENT_DISPLAY_STATE_CHANGED -> DisplayChange.STATE
                else -> return
            }
            listener?.onDisplayChanged(displayId, change)
        }

        override fun onDisplaySnapshot(displayIds: IntArray?, events: IntArray?) {}

        override fun onTopologyChanged(topology: DisplayTopology?) {}
    }

    override fun displays(): List<DisplayInfo> = service.call {
        getDisplayIds(false).toList().mapNotNull { id ->
            val info = getDisplayInfo(id) ?: return@mapNotNull null
            DisplayInfo(id, info.uniqueId.orEmpty(), info.type, info.logicalWidth, info.logicalHeight, info.state)
        }
    }

    override fun brightness(displayId: Int): Float = service.call { getBrightness(displayId) }

    override fun setBrightness(displayId: Int, value: Float) = service.call { setBrightness(displayId, value) }

    override fun requestDisplayPower(displayId: Int, state: Int): Boolean = service.call { requestDisplayPower(displayId, state) }

    @Synchronized
    override fun watch(listener: DisplayChangeListener) {
        this.listener = listener
        val binder = service.binder() ?: throw SystemServiceUnavailableException(Context.DISPLAY_SERVICE)
        if (binder === watchedOn) return
        service.call { registerCallbackWithEventMask(callback, EVENTS) }
        watchedOn = binder
    }

    private companion object {
        // DisplayManagerGlobal's INTERNAL_EVENT_FLAG_DISPLAY_* and EVENT_DISPLAY_* (Android 17).
        const val FLAG_DISPLAY_ADDED = 1L shl 2
        const val FLAG_DISPLAY_STATE = 1L shl 5
        const val FLAG_DISPLAY_REMOVED = 1L shl 9
        const val EVENTS = FLAG_DISPLAY_ADDED or FLAG_DISPLAY_REMOVED or FLAG_DISPLAY_STATE
        const val EVENT_DISPLAY_ADDED = 2
        const val EVENT_DISPLAY_STATE_CHANGED = 16
        const val EVENT_DISPLAY_REMOVED = 256
    }
}
