package com.thorctl.protocol.ipc;

parcelable GamepadStateParcel {
    boolean on;
    /** GamepadTransport.wire */
    String transport;
    boolean connected;
    String host;
    boolean screensOff;
    boolean dark;
    String error;
}
