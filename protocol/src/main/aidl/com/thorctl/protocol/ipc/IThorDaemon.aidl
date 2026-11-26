package com.thorctl.protocol.ipc;

import com.thorctl.protocol.ipc.FanProfileParcel;
import com.thorctl.protocol.ipc.GameOptionsParcel;
import com.thorctl.protocol.ipc.IThorClient;
import com.thorctl.protocol.ipc.LedStateParcel;

/**
 * The root daemon (thorctld). It hands this binder to the app through the app's
 * DaemonBinderProvider (see ThorIpc). Only root, system, shell and the app's own uid may call it.
 * Commands are oneway: the daemon answers by pushing a new state to every client.
 */
interface IThorDaemon {
    /** Bumped whenever this interface or a parcelable changes. Clients check it before registering. */
    const int VERSION = 1;

    int getVersion();

    /** Adds a client; it gets the full state right away. A dead client is dropped by itself. */
    void register(IThorClient client);

    void unregister(IThorClient client);

    /** Live telemetry once per second for [client] while [on]. */
    oneway void setStatsSubscribed(IThorClient client, boolean on);

    oneway void setFanProfile(String id);

    oneway void saveFanProfile(in FanProfileParcel profile, boolean activate);

    oneway void deleteFanProfile(String id);

    /** [screen]: Screen.wire. [isFinal] is false while a slider is dragged (no state echo). */
    oneway void setBrightness(String screen, float value, boolean isFinal);

    /** ControllerTarget.wire */
    oneway void setController(String target);

    /** Moves the app in front on the other screen to [to] (Screen.wire). */
    oneway void moveApp(String to);

    oneway void setLeds(in LedStateParcel state);

    oneway void setBottomPower(boolean on);

    oneway void setKeepAccessibility(boolean on);

    oneway void setGameMode(boolean on);

    oneway void setGameOptions(in GameOptionsParcel options);

    oneway void recentsOpen(int taskId);

    oneway void recentsHome();

    oneway void recentsRemove(int taskId);

    oneway void recentsClear(in int[] taskIds);

    oneway void recentsClose();

    /** The Home-press overlay's first frame is on screen: the daemon may now send Home. */
    oneway void homeGo(int seq);

    /** The recents / Home overlay is gone. */
    oneway void overlayDone();

    /** Thor Keyboard is on screen: L1 / R1 act as its shift key. */
    oneway void keyboardVisible(boolean on);

    oneway void setGamepad(boolean on);

    /** GamepadTransport.wire */
    oneway void setGamepadTransport(String transport);

    oneway void setGamepadScreensOff(boolean on);

    /** The app's Bluetooth HID link to a host came up or went down. */
    oneway void gamepadBluetooth(boolean connected, String host);
}
