package android.os;

/** Hidden binder interface (compile-time stub; the framework's own class is used at runtime). */
public interface IPowerManager extends IInterface {
    boolean isInteractive() throws RemoteException;

    void userActivity(int displayId, long time, int event, int flags) throws RemoteException;

    abstract class Stub extends Binder implements IPowerManager {
        public static IPowerManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
