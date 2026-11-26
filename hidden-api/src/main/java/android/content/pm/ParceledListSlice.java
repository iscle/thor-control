package android.content.pm;

import android.os.Parcelable;
import java.util.List;

/** Hidden: a list sent over binder in slices (compile-time stub). */
public class ParceledListSlice<T extends Parcelable> {
    public List<T> getList() {
        throw new RuntimeException("Stub!");
    }
}
