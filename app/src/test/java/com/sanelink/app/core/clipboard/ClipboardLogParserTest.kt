package com.sanelink.app.core.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardLogParserTest {

    private val pkg = "com.gamerjagdish.sanelink"

    @Test
    fun `matches Android 10+ brief denial line for own package`() {
        val line = "E/ClipboardService( 1432): Denying clipboard access to com.gamerjagdish.sanelink, " +
            "application is not in focus nor is it a system service for user 0"
        assertTrue(ClipboardLogParser.isClipboardDenialFor(line, pkg))
    }

    @Test
    fun `matches threadtime format and line ending right after package`() {
        val threadtime = "11-26 10:01:02.345  1432  1500 E ClipboardService: Denying clipboard access to $pkg, application is not in focus"
        assertTrue(ClipboardLogParser.isClipboardDenialFor(threadtime, pkg))
        assertTrue(ClipboardLogParser.isClipboardDenialFor("Denying clipboard access to $pkg", pkg))
    }

    @Test
    fun `ignores other packages and prefix collisions`() {
        val other = "E/ClipboardService( 1432): Denying clipboard access to org.kde.kdeconnect_tp, application is not in focus"
        assertFalse(ClipboardLogParser.isClipboardDenialFor(other, pkg))

        val longer = "E/ClipboardService( 1432): Denying clipboard access to $pkg.debug, application is not in focus"
        assertFalse(ClipboardLogParser.isClipboardDenialFor(longer, pkg))
    }

    @Test
    fun `finds own package when several denials share a line`() {
        val line = "Denying clipboard access to com.gamerjagdish.sanelinkx, Denying clipboard access to $pkg, foo"
        assertTrue(ClipboardLogParser.isClipboardDenialFor(line, pkg))
    }

    @Test
    fun `ignores unrelated clipboard lines and empty package`() {
        assertFalse(ClipboardLogParser.isClipboardDenialFor("I/ClipboardService( 1432): Clipboard changed", pkg))
        assertFalse(ClipboardLogParser.isClipboardDenialFor("Denying clipboard access to $pkg", ""))
    }

    @Test
    fun `logcat command only streams ClipboardService errors from epoch start time`() {
        assertEquals(
            listOf("logcat", "-v", "brief", "-T", "1795600000.042", "ClipboardService:E", "*:S"),
            ClipboardLogParser.buildLogcatCommand(1_795_600_000_042L)
        )
        assertEquals("1795600000.000", ClipboardLogParser.buildLogcatCommand(1_795_600_000_000L)[4])
    }
}
