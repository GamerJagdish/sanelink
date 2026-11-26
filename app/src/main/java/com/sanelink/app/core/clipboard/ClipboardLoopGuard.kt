package com.sanelink.app.core.clipboard

/**
 * Prevents feedback loops and duplicate work in the clipboard pipeline.
 *
 * - Writing the clean link back changes the clipboard, which fires every change signal
 *   again (listener, logcat denial line, system "Copied" overlay). Those echoes arrive
 *   within a short window after our own write and are ignored.
 * - The same copy can be reported by several signals at once (logcat + accessibility);
 *   focused reads are throttled so the invisible reader only opens once.
 * - Text we already processed (or wrote ourselves) is never processed again.
 *
 * Thread-safe; the clock is injectable for tests.
 */
class ClipboardLoopGuard(
    private val clock: () -> Long = System::currentTimeMillis,
    private val echoWindowMillis: Long = 1_200L,
    private val launchThrottleMillis: Long = 800L
) {
    private var lastWriteAt = Long.MIN_VALUE / 2
    private var lastLaunchAt = Long.MIN_VALUE / 2
    private var lastWrittenText: String? = null
    private var lastSeenText: String? = null

    /** Records that SaneLink itself just put [text] on the clipboard. */
    @Synchronized
    fun recordOwnWrite(text: String) {
        lastWriteAt = clock()
        lastWrittenText = text
        lastSeenText = text
    }

    /** True while change signals are most likely echoes of our own write. */
    @Synchronized
    fun isInEchoWindow(): Boolean = clock() - lastWriteAt < echoWindowMillis

    /**
     * Call before opening the invisible focus reader. Returns false (and does nothing)
     * if this signal is an echo or duplicate of one handled moments ago.
     */
    @Synchronized
    fun tryAcquireFocusedRead(): Boolean {
        if (isInEchoWindow()) return false
        val now = clock()
        if (now - lastLaunchAt < launchThrottleMillis) return false
        lastLaunchAt = now
        return true
    }

    /**
     * Returns true if [text] should be sanitized: it is neither the text we wrote nor
     * text we already looked at. Marks it as seen.
     */
    @Synchronized
    fun shouldProcess(text: String): Boolean {
        if (text == lastWrittenText || text == lastSeenText) return false
        lastSeenText = text
        return true
    }
}
