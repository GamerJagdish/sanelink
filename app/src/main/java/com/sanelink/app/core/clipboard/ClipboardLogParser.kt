package com.sanelink.app.core.clipboard

/**
 * Parses system log lines emitted by Android's `ClipboardService`.
 *
 * Since Android 10, whenever the clipboard changes, the system tries to notify every
 * registered `OnPrimaryClipChangedListener`. For listeners whose app is not in focus,
 * the notification is dropped and `ClipboardService` logs:
 *
 * ```
 * E ClipboardService: Denying clipboard access to com.example, application is not in focus nor is it a system service for user 0
 * ```
 *
 * Seeing that line for our own package is therefore a reliable, app-agnostic
 * "the clipboard just changed" signal (the technique used by KDE Connect, Join and
 * MacroDroid). Reading other apps' log lines requires `READ_LOGS`, granted once over ADB.
 */
object ClipboardLogParser {

    const val LOG_TAG = "ClipboardService"
    private const val DENIAL_PREFIX = "Denying clipboard access to "

    /** Returns true if [line] reports a denied clipboard notification for [packageName]. */
    fun isClipboardDenialFor(line: String, packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        var searchFrom = 0
        while (true) {
            val idx = line.indexOf(DENIAL_PREFIX, searchFrom)
            if (idx < 0) return false
            val pkgStart = idx + DENIAL_PREFIX.length
            if (line.startsWith(packageName, pkgStart)) {
                val end = pkgStart + packageName.length
                // Reject prefix matches such as "com.foo" vs "com.foo.bar".
                if (end == line.length) return true
                val next = line[end]
                if (next == ',' || next.isWhitespace()) return true
            }
            searchFrom = pkgStart
        }
    }

    /**
     * Arguments for a `logcat` process that streams only `ClipboardService` errors
     * logged from [startTimeMillis] onwards (so stale lines from before are not replayed).
     *
     * The start time uses logcat's epoch `sssss.mmm` form, so it doesn't depend on the
     * caller's time zone (the Shizuku shell process may not share the app's).
     */
    fun buildLogcatCommand(startTimeMillis: Long): List<String> {
        val since = "${startTimeMillis / 1000}.${(startTimeMillis % 1000).toString().padStart(3, '0')}"
        return listOf(
            "logcat",
            "-v", "brief",
            "-T", since,
            "$LOG_TAG:E",
            "*:S"
        )
    }
}
