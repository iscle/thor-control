package com.thorctl.daemon.system

import java.lang.reflect.Field

/** android.app.ContentProviderHolder (hidden), resolved once. */
private val providerField: Field by lazy { Class.forName("android.app.ContentProviderHolder").getDeclaredField("provider").apply { isAccessible = true } }

/** The holder's IContentProvider (see IContentProviderKtx.kt), or null. */
internal fun Any.contentProvider(): Any? = providerField.get(this)
