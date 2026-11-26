package com.thorctl.protocol.ipc;

import com.thorctl.protocol.ipc.FanProfileParcel;
import com.thorctl.protocol.ipc.GamepadStateParcel;
import com.thorctl.protocol.ipc.GameStateParcel;
import com.thorctl.protocol.ipc.LedStateParcel;

parcelable DaemonStateParcel {
    String fanProfile;
    FanProfileParcel[] profiles;
    float brightnessTop;
    float brightnessBottom;
    /** ControllerTarget.wire */
    String controller;
    LedStateParcel leds;
    boolean bottomOn;
    int topDisplayId;
    int bottomDisplayId;
    boolean fanKernelMode;
    String fanOverride;
    GameStateParcel game;
    GamepadStateParcel gamepad;
}
