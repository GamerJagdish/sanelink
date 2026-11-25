package com.sanelink.app.core.sanitizer

import com.sanelink.app.core.model.ClearUrlsData
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.core.model.DomainSwap
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Frontend domain swaps from built-in patches and simple-mode custom rules. */
class FrontendSwapSanitizerTest {

    private val baseData: ClearUrlsData by lazy {
        val rulesFile = File("../rules/rules.json").let { if (it.exists()) it else File("rules/rules.json") }
        Json { ignoreUnknownKeys = true }.decodeFromString<ClearUrlsData>(rulesFile.readText())
    }

    private fun withProviderSwap(providerId: String, swap: DomainSwap): ClearUrlsData {
        val provider = baseData.providers.getValue(providerId)
        return ClearUrlsData(baseData.providers + (providerId to provider.copy(frontendSwaps = listOf(swap))))
    }

    @Test
    fun `patched built-in rule strips trackers and then swaps the frontend`() {
        val data = withProviderSwap("instagram", DomainSwap("instagram.com", "oginstagram.com"))
        val result = UrlSanitizer(data).sanitizeSingleUrl("https://www.instagram.com/reel/C1x2?igsh=abc123&utm_source=ig_web")

        assertTrue(result.wasModified)
        assertEquals("https://oginstagram.com/reel/C1x2", result.cleanText)
        assertTrue(result.removedParameters.contains("igsh"))
    }

    @Test
    fun `swap alone still counts as a modification`() {
        val data = withProviderSwap("instagram", DomainSwap("instagram.com", "oginstagram.com"))
        val result = UrlSanitizer(data).sanitizeSingleUrl("https://www.instagram.com/p/abc")
        assertTrue(result.wasModified)
        assertEquals("https://oginstagram.com/p/abc", result.cleanText)
        assertTrue(result.removedParameters.isEmpty())
    }

    @Test
    fun `swapped link is stable when cleaned again`() {
        val data = withProviderSwap("instagram", DomainSwap("instagram.com", "oginstagram.com"))
        val sanitizer = UrlSanitizer(data)
        val once = sanitizer.sanitizeSingleUrl("https://instagram.com/p/abc?igsh=1").cleanText
        val twice = sanitizer.sanitizeSingleUrl(once)
        assertEquals(once, twice.cleanText)
        assertTrue(!twice.wasModified)
    }

    @Test
    fun `simple custom rule keeps built-in cleaning and swaps domain`() {
        val rule = CustomRule(
            name = "Reddit to Redlib",
            urlPattern = DomainSwapper.urlPatternFor("reddit.com"),
            matchDomain = "reddit.com",
            swapToDomain = "redlib.catsarch.com",
            stripParameters = listOf("share_id"),
            keepBuiltInCleaning = true
        )
        val result = UrlSanitizer(baseData, listOf(rule))
            .sanitizeSingleUrl("https://www.reddit.com/r/android/comments/1?share_id=x&utm_source=share&utm_medium=web")

        assertEquals("https://redlib.catsarch.com/r/android/comments/1", result.cleanText)
        assertTrue(result.removedParameters.containsAll(listOf("share_id", "utm_source", "utm_medium")))
    }

    @Test
    fun `custom rule without built-in cleaning still overrides built-ins but swaps`() {
        val rule = CustomRule(
            name = "Only my params",
            urlPattern = DomainSwapper.urlPatternFor("reddit.com"),
            matchDomain = "reddit.com",
            swapToDomain = "redlib.catsarch.com",
            stripParameters = listOf("share_id"),
            keepBuiltInCleaning = false
        )
        val result = UrlSanitizer(baseData, listOf(rule))
            .sanitizeSingleUrl("https://reddit.com/r/a?share_id=x&utm_source=share")

        assertEquals("https://redlib.catsarch.com/r/a?utm_source=share", result.cleanText)
    }

    @Test
    fun `custom rule swap wins over a built-in patch swap`() {
        val data = withProviderSwap("instagram", DomainSwap("instagram.com", "oginstagram.com"))
        val rule = CustomRule(
            name = "Instagram elsewhere",
            urlPattern = DomainSwapper.urlPatternFor("instagram.com"),
            matchDomain = "instagram.com",
            swapToDomain = "ddinstagram.com",
            keepBuiltInCleaning = true
        )
        val result = UrlSanitizer(data, listOf(rule)).sanitizeSingleUrl("https://instagram.com/p/abc?igsh=1")
        assertEquals("https://ddinstagram.com/p/abc", result.cleanText)
    }
}
