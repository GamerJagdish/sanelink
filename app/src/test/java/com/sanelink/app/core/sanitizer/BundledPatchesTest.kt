package com.sanelink.app.core.sanitizer

import com.sanelink.app.core.model.SaneLinkPatchesData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Guards rules/patches.json: a typo there silently disables a rule at runtime. */
class BundledPatchesTest {

    private val patches: SaneLinkPatchesData by lazy {
        val f = File("../rules/patches.json").let { if (it.exists()) it else File("rules/patches.json") }
        Json { ignoreUnknownKeys = true }.decodeFromString<SaneLinkPatchesData>(f.readText())
    }

    @Test
    fun `every regex in patches json compiles`() {
        val broken = mutableListOf<String>()
        fun check(provider: String, kind: String, pattern: String) {
            try {
                Regex(pattern)
            } catch (e: Exception) {
                broken.add("$provider.$kind: $pattern (${e.message})")
            }
        }
        for ((id, patch) in patches.providers) {
            patch.urlPattern?.let { check(id, "urlPattern", it) }
            (patch.addRules + patch.addReferralMarketing).forEach { check(id, "rule", "^(?:$it)$") }
            patch.addRawRules.forEach { check(id, "rawRule", it) }
            patch.addExceptions.forEach { check(id, "exception", it) }
            patch.addRedirections.forEach { check(id, "redirection", it) }
        }
        if (broken.isNotEmpty()) fail("Invalid patterns:\n" + broken.joinToString("\n"))
    }

    @Test
    fun `threads patch covers both domains`() {
        val pattern = Regex(patches.providers.getValue("threads").urlPattern!!, RegexOption.IGNORE_CASE)
        assertTrue(pattern.containsMatchIn("https://www.threads.com/@user/post/abc"))
        assertTrue(pattern.containsMatchIn("https://threads.net/@user/post/abc"))
    }
}
