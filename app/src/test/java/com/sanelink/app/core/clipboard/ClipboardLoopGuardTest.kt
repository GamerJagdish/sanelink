package com.sanelink.app.core.clipboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardLoopGuardTest {

    private var now = 10_000L
    private val guard = ClipboardLoopGuard(clock = { now }, echoWindowMillis = 1_200, launchThrottleMillis = 800)

    @Test
    fun `own write is never processed again`() {
        assertTrue(guard.shouldProcess("https://x.com/a?utm_source=1"))
        guard.recordOwnWrite("https://x.com/a")
        now += 60_000
        assertFalse(guard.shouldProcess("https://x.com/a"))
    }

    @Test
    fun `focused reads are suppressed during the echo window after our write`() {
        guard.recordOwnWrite("clean")
        assertTrue(guard.isInEchoWindow())
        assertFalse(guard.tryAcquireFocusedRead())
        now += 1_199
        assertFalse(guard.tryAcquireFocusedRead())
        now += 1
        assertFalse(guard.isInEchoWindow())
        assertTrue(guard.tryAcquireFocusedRead())
    }

    @Test
    fun `duplicate signals for one copy open the reader once`() {
        assertTrue(guard.tryAcquireFocusedRead())
        now += 100
        assertFalse(guard.tryAcquireFocusedRead())
        now += 700
        assertTrue(guard.tryAcquireFocusedRead())
    }

    @Test
    fun `same text is processed once but a new copy of the dirty link is processed`() {
        val dirty = "https://shop.example/item?ref=abc"
        assertTrue(guard.shouldProcess(dirty))
        assertFalse(guard.shouldProcess(dirty))
        guard.recordOwnWrite("https://shop.example/item")
        // User copies the tracked link again later
        now += 5_000
        assertTrue(guard.shouldProcess(dirty))
    }
}
