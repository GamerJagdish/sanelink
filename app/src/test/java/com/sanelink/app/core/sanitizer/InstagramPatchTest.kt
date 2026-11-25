package com.sanelink.app.core.sanitizer

import com.sanelink.app.core.model.ClearUrlsData
import com.sanelink.app.core.model.SaneLinkPatchesData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Verifies the bundled Instagram patch (rules/patches.json) strips Instagram's rotating
 * 4-letter tracking parameters (lqvp, igvp, ugsj, ...) while keeping functional ones.
 */
class InstagramPatchTest {

    private lateinit var sanitizer: UrlSanitizer

    private fun rulesFile(name: String) = File("../rules/$name").let {
        if (it.exists()) it else File("rules/$name")
    }

    @Before
    fun setUp() {
        val json = Json { ignoreUnknownKeys = true }
        val base = json.decodeFromString<ClearUrlsData>(rulesFile("rules.json").readText())
        val patches = json.decodeFromString<SaneLinkPatchesData>(rulesFile("patches.json").readText())

        val instagram = base.providers.getValue("instagram")
        val patch = patches.providers.getValue("instagram")
        val patched = instagram.copy(
            rules = (instagram.rules + patch.addRules).filterNot { it in patch.removeRules }.distinct()
        )
        sanitizer = UrlSanitizer(ClearUrlsData(base.providers + ("instagram" to patched)), emptyList())
    }

    @Test
    fun stripsRandomFourLetterTrackingParam() {
        val result = sanitizer.sanitizeSingleUrl("https://www.instagram.com/reel/DflbEVCTM6u/?lqvp=NzZpd3Y0MG5yNW5r")

        assertTrue(result.wasModified)
        assertEquals("https://www.instagram.com/reel/DflbEVCTM6u/", result.cleanText)
    }

    @Test
    fun stripsOtherRotatingVariantsTogether() {
        val result = sanitizer.sanitizeSingleUrl(
            "https://www.instagram.com/p/ABC123/?igvp=MTIz&ugsj=NDU2&IGSH=eHl6"
        )

        assertEquals("https://www.instagram.com/p/ABC123/", result.cleanText)
    }

    @Test
    fun keepsFunctionalParameters() {
        val input = "https://www.instagram.com/accounts/login/?next=%2Freel%2FDflbEVCTM6u%2F"
        val result = sanitizer.sanitizeSingleUrl(input)

        assertFalse(result.wasModified)
        assertEquals(input, result.cleanText)
    }

    @Test
    fun keepsParametersThatAreNotExactlyFourLetters() {
        val input = "https://www.instagram.com/p/ABC123/?img_index=2"
        val result = sanitizer.sanitizeSingleUrl(input)

        assertEquals(input, result.cleanText)
    }
}
