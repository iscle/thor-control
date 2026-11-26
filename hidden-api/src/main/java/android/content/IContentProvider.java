package android.content;

import android.os.Bundle;
import android.os.IInterface;
import android.os.RemoteException;

/** Hidden: a content provider's binder interface (compile-time stub). */
public interface IContentProvider extends IInterface {
    Bundle call(AttributionSource attributionSource, String authority, String method, String arg, Bundle extras) throws RemoteException;
}
