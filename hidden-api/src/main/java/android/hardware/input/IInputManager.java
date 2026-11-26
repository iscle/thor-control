package android.hardware.input;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.view.InputDevice;
import android.view.InputEvent;

/** Hidden binder interface (compile-time stub; the framework's own class is used at runtime). */
public interface IInputManager extends IInterface {
    int[] getInputDeviceIds() throws RemoteException;

    InputDevice getInputDevice(int deviceId) throws RemoteException;

    void addUniqueIdAssociationByDescriptor(String inputDeviceDescriptor, String displayUniqueId) throws RemoteException;

    void removeUniqueIdAssociationByDescriptor(String inputDeviceDescriptor) throws RemoteException;

    boolean injectInputEvent(InputEvent ev, int mode) throws RemoteException;

    abstract class Stub extends Binder implements IInputManager {
        public static IInputManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
