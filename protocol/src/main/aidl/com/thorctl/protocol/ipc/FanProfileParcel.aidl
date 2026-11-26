package com.thorctl.protocol.ipc;

/** temps[i] (°C) → percents[i] (fan duty, 0..100). */
parcelable FanProfileParcel {
    String id;
    String name;
    float[] temps;
    float[] percents;
}
