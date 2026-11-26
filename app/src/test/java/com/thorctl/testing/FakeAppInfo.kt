package com.thorctl.testing

import com.thorctl.domain.repository.AppInfoRepository

class FakeAppInfo(private val labels: Map<String, String> = mapOf("com.game" to "Space Game")) : AppInfoRepository {
    override fun label(pkg: String): String = labels[pkg] ?: pkg
}
