package com.thorctl.protocol.ipc;

import com.thorctl.protocol.ipc.GameOptionsParcel;

parcelable GameStateParcel {
    boolean enabled;
    String packageName;
    boolean boosted;
    int freedMb;
    GameOptionsParcel options;
}
