package com.thorctl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The package layering inside the app module (the daemon is out of reach already: the app only
 * has it at runtime). `domain` is plain Kotlin; `data` and `platform` don't know about each other
 * or about `ui`; `ui` reaches the daemon and storage only through `domain`. `app` (Android's entry
 * points) and `di` wire everything and may use anything.
 */
class LayeringTest {
    private val root = File(System.getProperty("sources.path") ?: "src/main/java", "com/thorctl")

    private fun files(packageName: String) = File(root, packageName).walkTopDown().filter { it.extension == "kt" }.toList()

    /** Imports, and fully qualified class names in code (comments, strings and the package line removed). */
    private fun references(f: File): List<String> {
        val code = f.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//.*"), "")
            .replace(Regex("\"(\\\\.|[^\"\\\\])*\""), "\"\"")
            .replace(Regex("^package .*$", RegexOption.MULTILINE), "")
        val imports = Regex("^import ([\\w.]+)", RegexOption.MULTILINE).findAll(code).map { it.groupValues[1] }.toList()
        val body = code.replace(Regex("^import .*$", RegexOption.MULTILINE), "")
        val qualified = Regex("\\b([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+)\\.[A-Z]").findAll(body).map { it.groupValues[1] }.toList()
        return imports + qualified
    }

    private fun violations(packageName: String, forbidden: List<String>): List<String> = files(packageName).flatMap { f ->
        references(f).filter { ref -> forbidden.any { ref == it || ref.startsWith("$it.") } }.map { "${f.name}: $it" }
    }.distinct()

    @Test
    fun domainIsPlainKotlin() {
        assertTrue(files("domain").size >= 4)
        assertEquals(emptyList(), violations("domain", listOf("android", "androidx", "timber", "com.thorctl.data", "com.thorctl.platform", "com.thorctl.ui", "com.thorctl.app")))
    }

    @Test
    fun dataKnowsNoUiOrPlatform() {
        assertTrue(files("data").size >= 3)
        assertEquals(emptyList(), violations("data", listOf("com.thorctl.ui", "com.thorctl.platform", "com.thorctl.app", "androidx.compose")))
    }

    @Test
    fun platformKnowsNoUiOrData() {
        assertTrue(files("platform").size >= 5)
        assertEquals(emptyList(), violations("platform", listOf("com.thorctl.ui", "com.thorctl.data", "com.thorctl.app")))
    }

    @Test
    fun uiReachesTheDaemonOnlyThroughTheDomain() {
        assertTrue(files("ui").size >= 10)
        assertEquals(emptyList(), violations("ui", listOf("com.thorctl.data", "com.thorctl.app", "com.thorctl.protocol.ipc", "androidx.datastore")))
    }

    @Test
    fun theCheckFindsQualifiedNamesButNotComments() {
        val f = File.createTempFile("Bad", ".kt").apply {
            writeText(
                """
                package com.thorctl.domain
                import android.content.Context
                // androidx.compose.Foo in a comment is fine, so is "com.thorctl.ui.X"
                val x: com.thorctl.data.Thing? = null
                """.trimIndent(),
            )
        }
        assertEquals(listOf("android.content.Context", "com.thorctl.data"), references(f))
        f.delete()
    }
}
