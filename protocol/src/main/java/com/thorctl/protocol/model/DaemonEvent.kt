package com.thorctl.protocol.model

/** Everything the root daemon tells the app. */
sealed interface DaemonEvent {
    data class StateChanged(val state: DaemonState) : DaemonEvent

    data class StatsUpdated(val stats: Stats) : DaemonEvent

    /** A short AYN press: toggle the menu. */
    data object MenuButton : DaemonEvent

    /** A long Home press was recognised: confirm it with a heavy click. */
    data object Haptic : DaemonEvent

    /** Confirmation pill for an AYN shortcut on [displayId]. */
    data class Hud(val hud: HudEvent, val displayId: Int) : DaemonEvent

    /**
     * Recent apps open on [displayId]. [hero] is the screen as it was when Home went down (it
     * stands in for [originTask]'s preview), [home] the home screen; null when there is none.
     */
    data class RecentsOpened(
        val displayId: Int,
        val fromHome: Boolean,
        val originTask: Int,
        val hero: Jpeg?,
        val home: Jpeg?,
        val tasks: List<RecentTask>,
    ) : DaemonEvent

    /** A fresher preview of [taskId]. */
    data class RecentsThumb(val taskId: Int, val thumb: Jpeg) : DaemonEvent

    /** A controller button while recents is open (the daemon holds the controller). */
    data class RecentsKey(val key: String) : DaemonEvent

    data object RecentsClosed : DaemonEvent

    /** A Home press inside an app: zoom from [app] (the screen when Home went down) to [home]. */
    data class HomeAnimation(val seq: Int, val displayId: Int, val app: Jpeg?, val home: Jpeg?) : DaemonEvent

    /** A controller key for Thor Keyboard ("shift" for L1 / R1). */
    data class KeyboardKey(val key: String) : DaemonEvent

    /** One gamepad report ([com.thorctl.protocol.GamepadHid] payload, no report ID) for the Bluetooth link. */
    class GamepadReport(val report: ByteArray) : DaemonEvent
}
