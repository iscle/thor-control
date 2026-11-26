package com.thorctl.protocol.ipc;

parcelable RecentTaskParcel {
    int id;
    String packageName;
    int displayId;
    boolean visible;
    boolean running;
    /** Read end of a pipe carrying the preview JPEG, or null. */
    @nullable ParcelFileDescriptor thumb;
}
