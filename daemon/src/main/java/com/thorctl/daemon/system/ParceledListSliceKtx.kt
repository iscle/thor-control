package com.thorctl.daemon.system

import java.lang.reflect.Method

/** android.content.pm.ParceledListSlice (hidden), resolved once. */
private val getListMethod: Method by lazy { Class.forName("android.content.pm.ParceledListSlice").getMethod("getList") }

/** The items of this ParceledListSlice. */
internal fun Any.parceledList(): List<*>? = getListMethod.invoke(this) as? List<*>
