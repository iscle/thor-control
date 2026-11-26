package android.content.pm;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import java.util.List;

/** Hidden binder interface (compile-time stub; the framework's own class is used at runtime). */
public interface IPackageManager extends IInterface {
    ApplicationInfo getApplicationInfo(String packageName, long flags, int userId) throws RemoteException;

    ComponentName getHomeActivities(List<ResolveInfo> outHomeCandidates) throws RemoteException;

    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
