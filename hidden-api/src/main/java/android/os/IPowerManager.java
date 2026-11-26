package android.os;


/** Hidden binder interface (compile-time stub; the framework's own class is used at runtime). */
public interface IPowerManager extends android.os.IInterface {
    boolean isInteractive() throws RemoteException;

    void userActivity(int displayId, long time, int event, int flags) throws RemoteException;

    abstract class Stub extends android.os.Binder implements IPowerManager {
        public static IPowerManager asInterface(android.os.IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
