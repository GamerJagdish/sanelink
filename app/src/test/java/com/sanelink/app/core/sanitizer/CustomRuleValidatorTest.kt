package com.sanelink.app.core.sanitizer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRuleValidatorTest {

    @Test
    fun `valid and blank patterns pass`() {
        assertNull(CustomRuleValidator.regexError("^https?://(?:www\\.)?reddit\\.com"))
        assertNull(CustomRuleValidator.regexError(""))
    }

    @Test
    fun `broken patterns are reported`() {
        assertNotNull(CustomRuleValidator.regexError("^https?://(reddit"))
        assertNotNull(CustomRuleValidator.regexError("*.reddit.com"))
    }

    @Test
    fun `rewrite needs both halves`() {
        assertNull(CustomRuleValidator.rewriteError("", ""))
        assertNotNull(CustomRuleValidator.rewriteError("^https://a\\.com/(.*)", ""))
        assertNotNull(CustomRuleValidator.rewriteError("", "https://b.com/$1"))
    }

    @Test
    fun `replacement group references must exist`() {
        assertNull(CustomRuleValidator.rewriteError("^https://a\\.com/(.*)", "https://b.com/$1"))
        assertNotNull(CustomRuleValidator.rewriteError("^https://a\\.com/.*", "https://b.com/$1"))
        assertNotNull(CustomRuleValidator.rewriteError("^https://a\\.com/(.*)", "https://b.com/$2"))
        // Escaped dollar is literal text, not a reference
        assertNull(CustomRuleValidator.rewriteError("^https://a\\.com/.*", "https://b.com/\\$5"))
    }

    @Test
    fun `empty simple rule is only a no-op when it keeps built-in cleaning`() {
        assertTrue(CustomRuleValidator.isNoOpSimpleRule(keepBuiltIn = true, paramCount = 0, hasSwap = false))
        assertFalse(CustomRuleValidator.isNoOpSimpleRule(keepBuiltIn = false, paramCount = 0, hasSwap = false))
        assertFalse(CustomRuleValidator.isNoOpSimpleRule(keepBuiltIn = true, paramCount = 1, hasSwap = false))
        assertFalse(CustomRuleValidator.isNoOpSimpleRule(keepBuiltIn = true, paramCount = 0, hasSwap = true))
    }
}
