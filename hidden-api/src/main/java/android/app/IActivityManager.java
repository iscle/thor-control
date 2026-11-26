package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import java.util.List;

/** Hidden binder interface (compile-time stub; the framework's own class is used at runtime). */
public interface IActivityManager extends IInterface {
    List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() throws RemoteException;

    void forceStopPackage(String packageName, int userId) throws RemoteException;

    void killBackgroundProcesses(String packageName, int userId) throws RemoteException;

    ContentProviderHolder getContentProviderExternal(String name, int userId, IBinder token, String tag) throws RemoteException;

    void removeContentProviderExternalAsUser(String name, IBinder token, int userId) throws RemoteException;

    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
