package android.hardware.display;

import android.os.RemoteException;
import android.view.DisplayInfo;

/** Hidden binder interface (compile-time stub; the framework's own class is used at runtime). */
public interface IDisplayManager extends android.os.IInterface {
    int[] getDisplayIds(boolean includeDisabled) throws RemoteException;

    DisplayInfo getDisplayInfo(int displayId) throws RemoteException;

    float getBrightness(int displayId) throws RemoteException;

    void setBrightness(int displayId, float brightness) throws RemoteException;

    boolean requestDisplayPower(int displayId, int state) throws RemoteException;

    abstract class Stub extends android.os.Binder implements IDisplayManager {
        public static IDisplayManager asInterface(android.os.IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
