package com.sanelink.app.core.sanitizer

import com.sanelink.app.core.model.ClearUrlsData
import com.sanelink.app.core.model.CustomRule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Regression tests for provider scoping, URL parsing and text handling. */
class UrlSanitizerCorrectnessTest {

    private val data: ClearUrlsData by lazy {
        val f = File("../rules/rules.json").let { if (it.exists()) it else File("rules/rules.json") }
        Json { ignoreUnknownKeys = true }.decodeFromString<ClearUrlsData>(f.readText())
    }
    private val sanitizer by lazy { UrlSanitizer(data) }

    // --- Redirections only apply on their own provider's site -------------------------------

    @Test
    fun `redirect rules of other providers do not unwrap unrelated sites`() {
        // rutracker's ".*url=(...)" used to unwrap return_url on any website
        val login = "https://bank.example/login?return_url=https%3A%2F%2Fbank.example%2Fhome"
        assertEquals(login, sanitizer.sanitizeSingleUrl(login).cleanText)

        // instagram's ".*u=(...)" used to send ?menu=... links to the embedded site
        val share = "https://news.example/share?menu=https%3A%2F%2Fother.example%2F"
        assertEquals(share, sanitizer.sanitizeSingleUrl(share).cleanText)
    }

    @Test
    fun `redirects still unwrap on their own site`() {
        val ig = "https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2Fa%3Futm_source%3Dig&e=AT0"
        assertEquals("https://example.com/a", sanitizer.sanitizeSingleUrl(ig).cleanText)
    }

    @Test
    fun `exclusive custom rule is not overridden by built-in redirects`() {
        val custom = UrlSanitizer(
            data,
            listOf(CustomRule(name = "mine", urlPattern = "^https?://www\\.google\\.com", stripParameters = listOf("sa")))
        )
        val url = "https://www.google.com/url?q=https%3A%2F%2Fexample.com%2F&sa=D"
        assertEquals("https://www.google.com/url?q=https%3A%2F%2Fexample.com%2F", custom.sanitizeSingleUrl(url).cleanText)
    }

    @Test
    fun `google amp links unwrap to the publisher`() {
        val result = sanitizer.sanitizeSingleUrl("https://www.google.com/amp/s/example.com/article?utm_source=x")
        assertEquals("https://example.com/article", result.cleanText)
    }

    @Test
    fun `upper-case scheme in a redirect target is accepted`() {
        val result = sanitizer.sanitizeSingleUrl("https://www.google.com/url?q=HTTPS%3A%2F%2Fexample.com%2Fa%3Futm_source%3Dx")
        assertEquals("HTTPS://example.com/a", result.cleanText)
    }

    @Test
    fun `decoded redirect targets never contain spaces`() {
        val result = sanitizer.sanitizeSingleUrl("https://www.google.com/url?q=https://example.com/a+b")
        assertFalse(result.cleanText.contains(' '))
    }

    @Test
    fun `deeply nested redirects stop at a safe depth`() {
        var url = "https://example.com/?utm_source=a"
        repeat(8) { url = "https://www.google.com/url?q=" + java.net.URLEncoder.encode(url, "UTF-8") }
        val result = sanitizer.sanitizeSingleUrl(url)
        assertTrue(result.wasModified)
    }

    // --- globalRules exceptions ---------------------------------------------------------------

    @Test
    fun `global rule exceptions are respected`() {
        val gitlab = "https://gitlab.com/group/proj?utm_source=docs&ref=main"
        assertEquals(gitlab, sanitizer.sanitizeSingleUrl(gitlab).cleanText)

        val google = "https://accounts.google.com/signin?utm_source=x&continue=1"
        assertEquals(google, sanitizer.sanitizeSingleUrl(google).cleanText)
    }

    @Test
    fun `global rules still apply everywhere else`() {
        assertEquals(
            "https://example.com/a?id=1",
            sanitizer.sanitizeSingleUrl("https://example.com/a?utm_source=x&id=1&fbclid=2").cleanText
        )
    }

    @Test
    fun `disabling global rules turns them off`() {
        val noGlobal = UrlSanitizer(data, disabledProviderIds = setOf("globalRules"))
        val url = "https://example.com/a?utm_source=x"
        assertEquals(url, noGlobal.sanitizeSingleUrl(url).cleanText)
    }

    // --- Parsing that java.net.URI used to reject ---------------------------------------------

    @Test
    fun `urls with characters URI rejects are still cleaned`() {
        assertEquals(
            "https://example.com/{x}?k=2",
            sanitizer.sanitizeSingleUrl("https://example.com/{x}?fbclid=1&k=2").cleanText
        )
    }

    @Test
    fun `credentials ports and fragments are preserved`() {
        assertEquals(
            "https://user:pw@example.com:8443/a?k=1#frag?x=1",
            sanitizer.sanitizeSingleUrl("https://user:pw@example.com:8443/a?utm_source=x&k=1#frag?x=1").cleanText
        )
    }

    // --- Text handling ------------------------------------------------------------------------

    @Test
    fun `sentence punctuation after a url is kept in the text`() {
        assertEquals(
            "Check https://example.com/a?id=1.",
            sanitizer.sanitize("Check https://example.com/a?utm_source=x&id=1.").cleanText
        )
        assertEquals(
            "(see https://example.com/a?id=1)",
            sanitizer.sanitize("(see https://example.com/a?id=1&fbclid=2)").cleanText
        )
    }

    @Test
    fun `balanced parentheses inside a url are kept`() {
        val url = "https://en.wikipedia.org/wiki/Mercury_(planet)"
        assertEquals("Read $url!", sanitizer.sanitize("Read $url!").cleanText)
    }

    @Test
    fun `trimmed length helper`() {
        assertEquals(19, UrlSanitizer.trimmedUrlLength("https://a.example/x.,!"))
        assertEquals(21, UrlSanitizer.trimmedUrlLength("https://a.example/(x)"))
        assertEquals(19, UrlSanitizer.trimmedUrlLength("https://a.example/x)"))
    }

    @Test
    fun `query stripper keeps unrelated structure`() {
        assertEquals(
            "https://e.x/p?b=2#h" to listOf("a"),
            QueryStripper.strip("https://e.x/p?a=1&b=2#h") { it == "a" }
        )
        assertEquals("https://e.x/p#h" to listOf("a"), QueryStripper.strip("https://e.x/p?a=1#h") { it == "a" })
        assertEquals("https://e.x/p" to emptyList<String>(), QueryStripper.strip("https://e.x/p") { true })
    }
}
