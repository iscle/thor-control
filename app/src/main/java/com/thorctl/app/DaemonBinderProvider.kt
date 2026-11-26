package com.thorctl.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.thorctl.protocol.ipc.ThorIpc
import timber.log.Timber

/**
 * Where the root daemon hands over its binder ([ThorIpc.METHOD_OFFER], root only) and where the
 * debug CLI asks for it ([ThorIpc.METHOD_GET], root or shell). It holds no data.
 */
class DaemonBinderProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val uid = Binder.getCallingUid()
        val providerContext = context ?: return null
        return when (method) {
            ThorIpc.METHOD_OFFER -> {
                if (uid != ThorIpc.UID_ROOT) {
                    Timber.w("binder offered by uid $uid: ignored")
                    return null
                }
                extras?.getBinder(ThorIpc.KEY_BINDER)?.let { providerContext.graph.daemonConnection.onDaemonBinder(it) }
                null
            }
            ThorIpc.METHOD_GET -> {
                if (uid != ThorIpc.UID_ROOT && uid != ThorIpc.UID_SHELL) return null
                Bundle().apply { putBinder(ThorIpc.KEY_BINDER, providerContext.graph.daemonConnection.currentBinder()) }
            }
            else -> null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
