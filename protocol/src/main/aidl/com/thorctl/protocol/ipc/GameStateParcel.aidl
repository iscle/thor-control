package com.thorctl.protocol.ipc;

import com.thorctl.protocol.ipc.GameOptionsParcel;

parcelable GameStateParcel {
    boolean enabled;
    String pkg;
    boolean boosted;
    int freedMb;
    GameOptionsParcel options;
}
