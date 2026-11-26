package com.sanelink.app.service

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

        val outcome = readCleanAndWrite(context, manual)
        if (outcome.first == Outcome.CLEANED || manual) {
            effectsScope.launch { afterClean(appContext, outcome.first, outcome.second, manual) }
        }
        return outcome.first
    }

    private suspend fun readCleanAndWrite(context: Context, manual: Boolean): Pair<Outcome, SanitizerResult?> {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return Outcome.NO_ACCESS to null
        // Returns null on Android 10+ when we are not allowed to read.
        val clip = try {
            cm.primaryClip
        } catch (_: SecurityException) {
            null
        } ?: return Outcome.NO_ACCESS to null
        if (clip.itemCount == 0) return Outcome.SKIPPED to null

        // Never touch content the source app flagged as sensitive (passwords, OTPs).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        ) {
            return Outcome.SKIPPED to null
        }

        // coerceToText also covers apps that copy a link as a URI item instead of text
        val text = clip.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) return Outcome.SKIPPED to null

        val guard = ClipboardMonitor.guard
        if (!guard.shouldProcess(text) && !manual) return Outcome.SKIPPED to null

        val result = SaneLinkApp.instance.repository.awaitSanitizer().sanitize(text)
        if (!result.wasModified || result.cleanText == text) return Outcome.ALREADY_CLEAN to result

        // Record before writing so the change echo is recognised as ours.
        guard.recordOwnWrite(result.cleanText)
        try {
            cm.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, result.cleanText))
        } catch (_: Exception) {
            return Outcome.NO_ACCESS to null
        }
        return Outcome.CLEANED to result
    }

    private suspend fun afterClean(context: Context, outcome: Outcome, result: SanitizerResult?, manual: Boolean) {
        val prefs = SaneLinkApp.instance.preferences
        if (outcome == Outcome.CLEANED && result != null) {
            prefs.recordSanitization(result.removedParameters.size)
            context.performClickHapticIfEnabled()
        }
        if (!prefs.showToastEnabled.first()) return
        val msg = when (outcome) {
            Outcome.CLEANED -> {
                val count = result?.removedParameters?.size ?: 0
                if (count > 0) "SaneLink: removed $count tracker${if (count > 1) "s" else ""}" else "SaneLink: clean link copied"
            }
            Outcome.ALREADY_CLEAN -> if (manual) "SaneLink: no tracking found" else null
            Outcome.SKIPPED -> if (manual) "SaneLink: nothing to clean on the clipboard" else null
            Outcome.NO_ACCESS -> if (manual) "SaneLink: couldn't read the clipboard" else null
        } ?: return
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
