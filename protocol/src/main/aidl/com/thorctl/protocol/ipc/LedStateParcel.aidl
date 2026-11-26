package com.thorctl.protocol.ipc;

parcelable LedStateParcel {
    boolean enabled;
    /** LedMode.wire */
    String mode;
    int left;
    int right;
    boolean sync;
    int brightness;
    float speed;
    boolean offWhenAsleep;
}
