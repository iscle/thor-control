package com.thorctl.protocol.ipc;

/** One HudEvent, flattened: [kind] says which of the other fields are set. */
parcelable HudParcel {
    /** "controller", "moved", "brightness", "nothing", "game" or "text". */
    String kind;
    int displayId;
    /** ControllerTarget.wire (controller) */
    String target;
    /** Screen.wire: moved to, brightness of, nothing on */
    String screen;
    /** moved, game */
    String packageName;
    /** brightness, 0..100 */
    int percent;
    /** GameHudState.wire */
    String gameState;
    int freedMb;
    String text;
}
