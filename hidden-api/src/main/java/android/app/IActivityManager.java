package android.app;

import android.os.IBinder;
import android.os.RemoteException;
import java.util.List;

/** Hidden binder interface (compile-time stub; the framework's own class is used at runtime). */
public interface IActivityManager extends android.os.IInterface {
    List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() throws RemoteException;

    void forceStopPackage(String packageName, int userId) throws RemoteException;

    void killBackgroundProcesses(String packageName, int userId) throws RemoteException;

    ContentProviderHolder getContentProviderExternal(String name, int userId, IBinder token, String tag) throws RemoteException;

    void removeContentProviderExternalAsUser(String name, IBinder token, int userId) throws RemoteException;

    abstract class Stub extends android.os.Binder implements IActivityManager {
        public static IActivityManager asInterface(android.os.IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
