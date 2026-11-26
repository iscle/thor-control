package android.hardware.display;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.view.DisplayInfo;

/** Hidden binder interface (compile-time stub; the framework's own class is used at runtime). */
public interface IDisplayManager extends IInterface {
    int[] getDisplayIds(boolean includeDisabled) throws RemoteException;

    DisplayInfo getDisplayInfo(int displayId) throws RemoteException;

    float getBrightness(int displayId) throws RemoteException;

    void setBrightness(int displayId, float brightness) throws RemoteException;

    boolean requestDisplayPower(int displayId, int state) throws RemoteException;

    void registerCallbackWithEventMask(IDisplayManagerCallback callback, long eventsMask) throws RemoteException;

    abstract class Stub extends Binder implements IDisplayManager {
        public static IDisplayManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
