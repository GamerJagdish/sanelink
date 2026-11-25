package com.sanelink.app.core.sanitizer

import com.sanelink.app.core.model.DomainSwap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainSwapperTest {

    private val ig = DomainSwap("instagram.com", "oginstagram.com")

    @Test
    fun `normalizes loose user input to a bare domain`() {
        assertEquals("instagram.com", DomainSwapper.normalizeDomain("https://www.Instagram.com/reel/abc?x=1"))
        assertEquals("instagram.com", DomainSwapper.normalizeDomain("  instagram.com  "))
        assertEquals("redlib.catsarch.com", DomainSwapper.normalizeDomain("redlib.catsarch.com/"))
        assertEquals("example.com", DomainSwapper.normalizeDomain("http://user@m.example.com:8080/"))
        assertNull(DomainSwapper.normalizeDomain("instagram"))
        assertNull(DomainSwapper.normalizeDomain("insta gram.com"))
        assertNull(DomainSwapper.normalizeDomain("-bad-.com"))
        assertNull(DomainSwapper.normalizeDomain(""))
    }

    @Test
    fun `swaps the site and its www and mobile aliases keeping the rest of the url`() {
        assertEquals(
            "https://oginstagram.com/reel/C1x2?igsh=abc#top",
            DomainSwapper.apply("https://www.instagram.com/reel/C1x2?igsh=abc#top", ig)
        )
        assertEquals("https://oginstagram.com/p/1", DomainSwapper.apply("https://instagram.com/p/1", ig))
        assertEquals("https://oginstagram.com", DomainSwapper.apply("https://m.instagram.com", ig))
        assertEquals("http://oginstagram.com:8080/x", DomainSwapper.apply("http://INSTAGRAM.com:8080/x", ig))
    }

    @Test
    fun `leaves other hosts and lookalikes untouched`() {
        assertNull(DomainSwapper.apply("https://help.instagram.com/123", ig))
        assertNull(DomainSwapper.apply("https://notinstagram.com/p/1", ig))
        assertNull(DomainSwapper.apply("https://instagram.com.evil.io/p/1", ig))
        assertNull(DomainSwapper.apply("https://example.com/?u=instagram.com", ig))
        assertNull(DomainSwapper.apply("ftp://instagram.com/p/1", ig))
        assertNull(DomainSwapper.apply("https://instagram.com/p/1", DomainSwap("instagram.com", "instagram.com")))
    }

    @Test
    fun `generated url pattern matches the domain and subdomains only`() {
        val regex = Regex(DomainSwapper.urlPatternFor("reddit.com"), RegexOption.IGNORE_CASE)
        assertTrue(regex.containsMatchIn("https://reddit.com"))
        assertTrue(regex.containsMatchIn("https://www.reddit.com/r/android?share_id=1"))
        assertTrue(regex.containsMatchIn("http://old.reddit.com:443/"))
        assertFalse(regex.containsMatchIn("https://notreddit.com/"))
        assertFalse(regex.containsMatchIn("https://reddit.com.evil.io/"))
        assertFalse(regex.containsMatchIn("https://example.com/?next=https://reddit.com"))
    }

    @Test
    fun `guesses a provider's main domain from its ClearURLs pattern`() {
        assertEquals("instagram.com", DomainSwapper.guessDomain("^https?:\\/\\/(?:[a-z0-9-]+\\.)*?instagram\\.com", "instagram"))
        assertEquals("reddit.com", DomainSwapper.guessDomain("^https?:\\/\\/(?:[a-z0-9-]+\\.)*?reddit.com", "reddit"))
        assertEquals("youtube.com", DomainSwapper.guessDomain("^https?:\\/\\/(?:[a-z0-9-]+\\.)*?(youtube\\.com|youtu\\.be)", "youtube"))
        assertEquals("amazon.com", DomainSwapper.guessDomain("^https?:\\/\\/(?:[a-z0-9-]+\\.)*?amazon(?:\\.[a-z]{2,}){1,}", "amazon"))
        assertEquals("tiktok.com", DomainSwapper.guessDomain("", "tiktok"))
    }
}
