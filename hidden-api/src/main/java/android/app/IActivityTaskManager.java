package android.app;

import android.content.pm.ParceledListSlice;
import android.os.Bundle;
import android.os.RemoteException;
import java.util.List;

/** Hidden binder interface (compile-time stub; the framework's own class is used at runtime). */
public interface IActivityTaskManager extends android.os.IInterface {
    ActivityTaskManager.RootTaskInfo getFocusedRootTaskInfo() throws RemoteException;

    void focusTopTask(int displayId) throws RemoteException;

    List<ActivityTaskManager.RootTaskInfo> getAllRootTaskInfosOnDisplay(int displayId) throws RemoteException;

    void moveRootTaskToDisplay(int taskId, int displayId) throws RemoteException;

    int startActivityFromRecents(int taskId, Bundle options) throws RemoteException;

    ParceledListSlice<ActivityManager.RecentTaskInfo> getRecentTasks(int maxNum, int flags, int userId) throws RemoteException;

    boolean removeTask(int taskId) throws RemoteException;

    abstract class Stub extends android.os.Binder implements IActivityTaskManager {
        public static IActivityTaskManager asInterface(android.os.IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
