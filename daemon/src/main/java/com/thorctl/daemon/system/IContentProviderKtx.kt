package com.thorctl.daemon.system

import android.content.AttributionSource
import android.os.Bundle
import java.lang.reflect.Method

/** android.content.IContentProvider (hidden), resolved once. */
private val callMethod: Method by lazy { Class.forName("android.content.IContentProvider").method("call", 5) }

/** ContentProvider.call on this IContentProvider. */
internal fun Any.callProvider(source: AttributionSource, authority: String, method: String, arg: String?, extras: Bundle?): Bundle? =
    callMethod.invoke(this, source, authority, method, arg, extras) as Bundle?
