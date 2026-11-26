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

    /**
     * System tag logged on every activity start or transition. Our app never logs it, so a
     * line with it proves the stream can read other processes' logs (i.e. log access works).
     */
    const val PROBE_TAG = "ActivityTaskManager"

    private val BRIEF_PID = Regex("""^[VDIWEF]/[^(]*\(\s*(\d+)\)""")
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
     *
     * With [includeProbe], informational [PROBE_TAG] lines are streamed too, so the reader can
     * tell whether Android actually granted access to other apps' logs.
     */
    fun buildLogcatCommand(startTimeMillis: Long, includeProbe: Boolean = false): List<String> {
        val since = "${startTimeMillis / 1000}.${(startTimeMillis % 1000).toString().padStart(3, '0')}"
        return buildList {
            add("logcat")
            add("-v"); add("brief")
            add("-T"); add(since)
            add("$LOG_TAG:E")
            if (includeProbe) add("$PROBE_TAG:I")
            add("*:S")
        }
    }

    /**
     * True when a `-v brief` [line] ("I/Tag( 575): ...") was logged by a process other than
     * [ownPid]. Android 13+ only shows other processes' lines once log access is allowed.
     */
    fun isFromOtherProcess(line: String, ownPid: Int): Boolean {
        val pid = BRIEF_PID.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return false
        return pid != ownPid
    }
}
