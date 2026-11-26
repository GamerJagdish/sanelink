package com.sanelink.app.service

import com.sanelink.app.core.history.RecentLink
import com.sanelink.app.core.history.CleanSource
import com.sanelink.app.util.trackersLabel
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.model.SanitizerResult
import com.sanelink.app.util.performClickHapticIfEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reads the current clipboard, strips trackers and writes the clean text back.
 *
 * Must be called from a context that is allowed to read the clipboard: on Android 10+
 * that means while one of our windows has input focus (see [ClipboardReaderActivity]),
 * or from a clipboard-change listener callback (which Android only delivers when access
 * is allowed). On Android 8–9 any context works.
 *
 * Returns as soon as the clipboard is updated; stats, haptics and the toast run afterwards
 * so the invisible reader can give focus back immediately.
 */
object ClipboardCleaner {

    enum class Outcome { CLEANED, ALREADY_CLEAN, SKIPPED, NO_ACCESS }

    private const val CLIP_LABEL = "SaneLink"

    /**
     * Puts [text] on the clipboard as SaneLink's own write, so background auto-clean
     * recognises the change echo and ignores it. Returns false if the write failed.
     */
    fun writeOwnClip(context: Context, text: String): Boolean {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        ClipboardMonitor.guard.recordOwnWrite(text)
        return try {
            cm.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True when the clipboard holds text SaneLink wrote itself. Only looks at the clip's
     * description, which (unlike reading the clip) doesn't make Android show its
     * "pasted from your clipboard" notice.
     */
    fun holdsOwnClip(cm: ClipboardManager): Boolean = try {
        cm.primaryClipDescription?.label == CLIP_LABEL
    } catch (_: Exception) {
        false
    }

    /**
     * Counts a cleaned link in the stats and adds it to the recent links log (if enabled).
     * Shared by every entry point that cleans a link.
     */
    suspend fun recordCleaned(original: String, result: SanitizerResult, source: CleanSource) {
        val prefs = SaneLinkApp.instance.preferences
        prefs.recordSanitization(result.removedParameters.size)
        prefs.addRecentLink(
            RecentLink(
                original = original,
                clean = result.cleanText,
                removed = result.removedParameters,
                source = source
            )
        )
    }

    private class Attempt(val outcome: Outcome, val original: String? = null, val result: SanitizerResult? = null)

    /** Outlives the reader activity, for work after the clipboard has been written. */
    private val effectsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * @param manual true when the user explicitly asked (Quick Settings tile): runs even if
     *   background protection is paused, re-checks text it has already seen, and reports
     *   when there was nothing to clean.
     */
    suspend fun cleanPrimaryClip(context: Context, manual: Boolean = false): Outcome {
        val app = SaneLinkApp.instance
        val appContext = context.applicationContext
        if (!manual && !app.preferences.autoSanitizeEnabled.first()) return Outcome.SKIPPED

        val attempt = readCleanAndWrite(context, manual)
        if (attempt.outcome == Outcome.CLEANED || manual) {
            effectsScope.launch { afterClean(appContext, attempt, manual) }
        }
        return attempt.outcome
    }

    private suspend fun readCleanAndWrite(context: Context, manual: Boolean): Attempt {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return Attempt(Outcome.NO_ACCESS)
        // Returns null on Android 10+ when we are not allowed to read.
        val clip = try {
            cm.primaryClip
        } catch (_: SecurityException) {
            null
        } ?: return Attempt(Outcome.NO_ACCESS)
        if (clip.itemCount == 0) return Attempt(Outcome.SKIPPED)

        // Never touch content the source app flagged as sensitive (passwords, OTPs).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        ) {
            return Attempt(Outcome.SKIPPED)
        }

        // coerceToText also covers apps that copy a link as a URI item instead of text
        val text = clip.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) return Attempt(Outcome.SKIPPED)

        val guard = ClipboardMonitor.guard
        if (!guard.shouldProcess(text) && !manual) return Attempt(Outcome.SKIPPED)

        val result = SaneLinkApp.instance.repository.awaitSanitizer().sanitize(text)
        if (!result.wasModified || result.cleanText == text) return Attempt(Outcome.ALREADY_CLEAN, text, result)

        if (!writeOwnClip(context, result.cleanText)) return Attempt(Outcome.NO_ACCESS)
        return Attempt(Outcome.CLEANED, text, result)
    }

    private suspend fun afterClean(context: Context, attempt: Attempt, manual: Boolean) {
        val prefs = SaneLinkApp.instance.preferences
        val outcome = attempt.outcome
        val result = attempt.result
        if (outcome == Outcome.CLEANED && result != null && attempt.original != null) {
            recordCleaned(attempt.original, result, if (manual) CleanSource.TILE else CleanSource.AUTO)
            context.performClickHapticIfEnabled()
        }
        if (!prefs.showToastEnabled.first()) return
        val msg = when (outcome) {
            Outcome.CLEANED -> {
                val count = result?.removedParameters?.size ?: 0
                if (count > 0) "SaneLink: removed ${trackersLabel(count)}" else "SaneLink: clean link copied"
            }
            Outcome.ALREADY_CLEAN -> if (manual) "SaneLink: no tracking found" else null
            Outcome.SKIPPED -> if (manual) "SaneLink: nothing to clean on the clipboard" else null
            Outcome.NO_ACCESS -> if (manual) "SaneLink: couldn't read the clipboard" else null
        } ?: return
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
