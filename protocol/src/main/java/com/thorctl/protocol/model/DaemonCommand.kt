package com.thorctl.protocol.model

/**
 * Everything the app (or the debug CLI) can ask the root daemon to do. Registering a client and
 * subscribing to stats are part of the connection, not commands (see `IThorDaemon`).
 */
sealed interface DaemonCommand {
    data class SetFanProfile(val id: String) : DaemonCommand

    data class SaveFanProfile(val profile: FanProfile, val activate: Boolean) : DaemonCommand

    data class DeleteFanProfile(val id: String) : DaemonCommand

    /** [final] is false while a slider is dragged (no state echo), true on release. */
    data class SetBrightness(val screen: Screen, val value: Float, val final: Boolean) : DaemonCommand

    data class SetController(val target: ControllerTarget) : DaemonCommand

    /** Moves the app in front on the other screen to [to]. */
    data class MoveApp(val to: Screen) : DaemonCommand

    data class SetLeds(val state: LedState) : DaemonCommand

    data class SetBottomPower(val on: Boolean) : DaemonCommand

    data class SetKeepAccessibility(val on: Boolean) : DaemonCommand

    data class SetGameMode(val on: Boolean) : DaemonCommand

    data class SetGameOptions(val options: GameOptions) : DaemonCommand

    data class RecentsOpen(val taskId: Int) : DaemonCommand

    data object RecentsHome : DaemonCommand

    data class RecentsRemove(val taskId: Int) : DaemonCommand

    data class RecentsClear(val taskIds: List<Int>) : DaemonCommand

    data object RecentsClose : DaemonCommand

    /** The Home-press overlay's first frame is on screen: the daemon may now send Home. */
    data class HomeGo(val seq: Int) : DaemonCommand

    /** The recents / Home overlay is gone. */
    data object OverlayDone : DaemonCommand

    /** Thor Keyboard is on screen: L1 / R1 act as its shift key. */
    data class KeyboardVisible(val on: Boolean) : DaemonCommand

    data class SetGamepad(val on: Boolean) : DaemonCommand

    data class SetGamepadTransport(val transport: GamepadTransport) : DaemonCommand

    data class SetGamepadScreensOff(val on: Boolean) : DaemonCommand

    /** The app's Bluetooth HID link to a host came up or went down. */
    data class GamepadBluetooth(val connected: Boolean, val host: String) : DaemonCommand
}
