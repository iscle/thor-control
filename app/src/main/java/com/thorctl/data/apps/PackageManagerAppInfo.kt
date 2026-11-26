package com.thorctl.data.apps

import android.content.Context
import com.thorctl.domain.repository.AppInfoRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.ConcurrentHashMap

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PackageManagerAppInfo(private val context: Context) : AppInfoRepository {
    // Labels hardly change and are asked for on every state push (the game's name).
    private val labels = ConcurrentHashMap<String, String>()

    override fun label(pkg: String): String {
        if (pkg.isEmpty()) return ""
        return labels.getOrPut(pkg) {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg
            }
        }
    }
}
