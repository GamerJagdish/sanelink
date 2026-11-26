package com.sanelink.app.core.backup

import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.core.model.DomainSwap
import com.sanelink.app.core.model.UserPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val reddit = CustomRule(id = "r", name = "Reddit", urlPattern = "^https?://reddit\\.com", matchDomain = "reddit.com", swapToDomain = "redlib.catsarch.com")
    private val x = CustomRule(id = "x", name = "X", urlPattern = "^https?://x\\.com", stripParameters = listOf("s", "t"))
    private val igPatch = UserPatch(providerId = "instagram", addedRules = listOf("abcd"), frontendSwaps = listOf(DomainSwap("instagram.com", "ddinstagram.com")))

    private val full = SaneLinkBackup(
        exportedAt = 123,
        customRules = listOf(reddit, x),
        userPatches = mapOf("instagram" to igPatch),
        disabledProviders = setOf("google"),
        settings = BackupSettings(autoSanitize = false, hapticFeedback = false, showToast = true, historyLimit = 50)
    )

    @Test
    fun `round trip keeps everything`() {
        val result = BackupCodec.decode(BackupCodec.encode(full))
        assertEquals(BackupParseResult.Success(full), result)
    }

    @Test
    fun `rejects text that isn't a backup`() {
        assertTrue(BackupCodec.decode("") is BackupParseResult.Error)
        assertTrue(BackupCodec.decode("https://example.com") is BackupParseResult.Error)
        assertTrue(BackupCodec.decode("""{"format":"something-else","version":1}""") is BackupParseResult.Error)
        assertTrue(BackupCodec.decode("""{"format":"sanelink-backup","version":99}""") is BackupParseResult.Error)
        // Cut off while copying
        assertTrue(BackupCodec.decode(BackupCodec.encode(full).dropLast(20)) is BackupParseResult.Error)
    }

    @Test
    fun `ignores unknown fields from future minor additions`() {
        val text = """{"format":"sanelink-backup","version":1,"somethingNew":true,"customRules":[]}"""
        assertTrue(BackupCodec.decode(text) is BackupParseResult.Success)
    }

    @Test
    fun `merge adds and overwrites rules but keeps the phone's settings`() {
        val current = SaneLinkBackup(
            customRules = listOf(x.copy(name = "Old X"), CustomRule(id = "mine", name = "Mine", urlPattern = ".")),
            userPatches = mapOf("amazon" to UserPatch(providerId = "amazon")),
            disabledProviders = setOf("bing"),
            settings = BackupSettings(historyLimit = 10)
        )
        val merged = BackupCodec.combine(current, full, replace = false)
        assertEquals(listOf("r", "x", "mine"), merged.customRules.map { it.id })
        assertEquals("X", merged.customRules.first { it.id == "x" }.name)
        assertEquals(setOf("amazon", "instagram"), merged.userPatches.keys)
        assertEquals(setOf("bing", "google"), merged.disabledProviders)
        assertEquals(10, merged.settings.historyLimit)
    }

    @Test
    fun `replace makes the backup the whole setup`() {
        val current = SaneLinkBackup(customRules = listOf(CustomRule(id = "mine", name = "Mine", urlPattern = ".")))
        val replaced = BackupCodec.combine(current, full, replace = true)
        assertEquals(full.customRules, replaced.customRules)
        assertEquals(full.settings, replaced.settings)
    }

    @Test
    fun `summary counts with plurals`() {
        assertEquals("2 custom rules, 1 patch, 1 switched-off rule", BackupCodec.summary(full))
    }
}
