package android.hardware.display;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/** Hidden: how the display manager tells a client about display events (compile-time stub). */
public interface IDisplayManagerCallback extends IInterface {
    void onDisplayEvent(int displayId, int event) throws RemoteException;

    void onDisplaySnapshot(int[] displayIds, int[] events) throws RemoteException;

    void onTopologyChanged(DisplayTopology topology) throws RemoteException;

    abstract class Stub extends Binder implements IDisplayManagerCallback {
        public Stub() {
            throw new RuntimeException("Stub!");
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Stub!");
        }

        public static IDisplayManagerCallback asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
