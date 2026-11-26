package com.thorctl.protocol.model

/** What an AYN shortcut did, shown as a short confirmation pill. */
sealed interface HudEvent {
    data class Controller(val target: ControllerTarget) : HudEvent

    data class Moved(val packageName: String, val to: Screen) : HudEvent

    /** [percent] is the slider position, 0..100. */
    data class Brightness(val screen: Screen, val percent: Int) : HudEvent

    /** There was no app on [from] to move. */
    data class NothingToMove(val from: Screen) : HudEvent

    data class Game(val state: GameHudState, val packageName: String = "", val freedMb: Int = 0) : HudEvent

    data class Text(val text: String) : HudEvent
}
