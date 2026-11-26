package com.thorctl.protocol.ipc;

import com.thorctl.protocol.ipc.DaemonStateParcel;
import com.thorctl.protocol.ipc.HudParcel;
import com.thorctl.protocol.ipc.RecentTaskParcel;
import com.thorctl.protocol.ipc.StatsParcel;

/**
 * Implemented by the app (and the debug CLI): what the daemon tells its clients. Every call is
 * oneway, so a slow client never stalls the daemon, and calls arrive in the order they were made.
 * Images are read ends of pipes the daemon writes the JPEG into: read them once, or close them.
 */
oneway interface IThorClient {
    void onState(in DaemonStateParcel state);

    void onStats(in StatsParcel stats);

    /** A short AYN press: toggle the menu. */
    void onMenuButton();

    /** A long Home press: confirm it with a heavy click. */
    void onHaptic();

    void onHud(in HudParcel hud);

    void onRecents(int displayId, boolean fromHome, int originTask, in @nullable ParcelFileDescriptor hero,
        in @nullable ParcelFileDescriptor home, in RecentTaskParcel[] tasks);

    void onRecentsThumb(int taskId, in ParcelFileDescriptor thumb);

    void onRecentsKey(String key);

    void onRecentsClosed();

    void onHomeAnimation(int seq, int displayId, in @nullable ParcelFileDescriptor app, in @nullable ParcelFileDescriptor home);

    void onKeyboardKey(String key);

    /** GamepadHid payload without the report ID. */
    void onGamepadReport(in byte[] report);
}
