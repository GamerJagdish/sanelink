package com.sanelink.app.core.sanitizer

import com.sanelink.app.core.model.ClearUrlsData
import com.sanelink.app.core.model.CustomRule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class UrlSanitizerTest {

    private lateinit var sanitizer: UrlSanitizer

    @Before
    fun setUp() {
        val rulesFile = File("../rules/rules.json").let {
            if (it.exists()) it else File("rules/rules.json")
        }
        val jsonText = rulesFile.readText()
        val json = Json { ignoreUnknownKeys = true }
        val clearUrlsData = json.decodeFromString<ClearUrlsData>(jsonText)

        val customRules = listOf(
            CustomRule(
                id = "reddit_redlib",
                name = "Reddit to Redlib",
                urlPattern = "^https?://(?:www\\.)?reddit\\.com",
                stripParameters = listOf("utm_source", "share_id", "rdt", "correlation_id"),
                rewritePattern = "^https?://(?:www\\.)?reddit\\.com/(.*)",
                rewriteReplacement = "https://redlib.catsarch.com/$1",
                isEnabled = true
            )
        )

        sanitizer = UrlSanitizer(clearUrlsData, customRules)
    }

    @Test
    fun testUniversalTrackingParametersStripped() {
        val input = "https://example.com/blog?utm_source=newsletter&utm_medium=email&utm_campaign=winter_sale&article_id=42&fbclid=IwAR123"
        val result = sanitizer.sanitizeSingleUrl(input)

        assertTrue(result.wasModified)
        assertEquals("https://example.com/blog?article_id=42", result.cleanText)
        assertTrue(result.removedParameters.contains("utm_source"))
        assertTrue(result.removedParameters.contains("utm_medium"))
        assertTrue(result.removedParameters.contains("utm_campaign"))
        assertTrue(result.removedParameters.contains("fbclid"))
    }

    @Test
    fun testAmazonAffiliateAndTrackingStripped() {
        val input = "https://www.amazon.com/dp/B08N5WRWNW?tag=affiliate-20&keywords=gadget&qid=1600000000&ref_=sr_1_1"
        val result = sanitizer.sanitizeSingleUrl(input)

        assertTrue(result.wasModified)
        assertEquals("https://www.amazon.com/dp/B08N5WRWNW", result.cleanText)
        assertTrue(result.removedParameters.contains("tag"))
        assertTrue(result.removedParameters.contains("keywords"))
        assertTrue(result.removedParameters.contains("qid"))
        assertTrue(result.removedParameters.contains("ref_"))
    }

    @Test
    fun testXTwitterParametersStripped() {
        val input = "https://twitter.com/jack/status/20?s=20&t=487abcdef123"
        val result = sanitizer.sanitizeSingleUrl(input)

        assertTrue(result.wasModified)
        assertEquals("https://twitter.com/jack/status/20", result.cleanText)
        assertTrue(result.removedParameters.contains("s"))
        assertTrue(result.removedParameters.contains("t"))
    }

    @Test
    fun testRedditTrackingStrippedAndFrontendRewrittenToRedlib() {
        val input = "https://www.reddit.com/r/Android/comments/12345/great_post/?utm_source=share&share_id=abc987&rdt=456"
        val result = sanitizer.sanitizeSingleUrl(input)

        assertTrue(result.wasModified)
        // Cleaned parameters AND rewritten frontend!
        assertEquals("https://redlib.catsarch.com/r/Android/comments/12345/great_post/", result.cleanText)
        assertTrue(result.removedParameters.contains("utm_source"))
        assertTrue(result.removedParameters.contains("share_id"))
        assertTrue(result.removedParameters.contains("rdt"))
    }

    @Test
    fun testGoogleRedirectUnwrap() {
        val input = "https://www.google.com/url?q=https%3A%2F%2Fgithub.com%2Ftrending%3Futm_source%3Dgoogle_search&sa=D&sntz=1"
        val result = sanitizer.sanitizeSingleUrl(input)

        assertTrue(result.wasModified)
        // Unwrapped AND recursively stripped nested utm_source!
        assertEquals("https://github.com/trending", result.cleanText)
    }

    @Test
    fun testRedditRedirectUnwrap() {
        val input = "https://out.reddit.com/url?url=https%3A%2F%2Fdeveloper.android.com%2F%3Ffbclid%3D123"
        val result = sanitizer.sanitizeSingleUrl(input)

        assertTrue(result.wasModified)
        assertEquals("https://developer.android.com/", result.cleanText)
    }

    @Test
    fun testEmbeddedUrlInMessagePreserved() {
        val input = "Hey check this tweet: https://twitter.com/user/status/999?s=19 - let me know!"
        val result = sanitizer.sanitize(input)

        assertTrue(result.wasModified)
        assertEquals("Hey check this tweet: https://twitter.com/user/status/999 - let me know!", result.cleanText)
    }

    @Test
    fun testCleanUrlRemainsUnchanged() {
        val input = "https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable"
        val result = sanitizer.sanitize(input)

        assertFalse(result.wasModified)
        assertEquals(input, result.cleanText)
    }
}
