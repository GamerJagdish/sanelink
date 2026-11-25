package com.sanelink.app.service

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import com.sanelink.app.SaneLinkApp
import kotlinx.coroutines.flow.first

/**
 * Reads the current clipboard, strips trackers and writes the clean text back.
 *
 * Must be called from a context that is allowed to read the clipboard: on Android 10+
 * that means while one of our windows has input focus (see [ClipboardReaderActivity]),
 * or from a clipboard-change listener callback (which Android only delivers when access
 * is allowed). On Android 8–9 any context works.
 */
object ClipboardCleaner {

    enum class Outcome { CLEANED, ALREADY_CLEAN, SKIPPED, NO_ACCESS }

    private const val CLIP_LABEL = "SaneLink"

    suspend fun cleanPrimaryClip(context: Context): Outcome {
        val app = SaneLinkApp.instance
        if (!app.preferences.autoSanitizeEnabled.first()) return Outcome.SKIPPED

        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return Outcome.NO_ACCESS
        // Returns null on Android 10+ when we are not allowed to read.
        val clip = try {
            cm.primaryClip
        } catch (_: SecurityException) {
            null
        } ?: return Outcome.NO_ACCESS
        if (clip.itemCount == 0) return Outcome.SKIPPED

        // Never touch content the source app flagged as sensitive (passwords, OTPs).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        ) {
            return Outcome.SKIPPED
        }

        val text = clip.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) return Outcome.SKIPPED

        val guard = ClipboardMonitor.guard
        if (!guard.shouldProcess(text)) return Outcome.SKIPPED

        val result = app.repository.getSanitizer().sanitize(text)
        if (!result.wasModified || result.cleanText == text) return Outcome.ALREADY_CLEAN

        // Record before writing so the change echo is recognised as ours.
        guard.recordOwnWrite(result.cleanText)
        try {
            cm.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, result.cleanText))
        } catch (_: Exception) {
            return Outcome.NO_ACCESS
        }

        app.preferences.recordSanitization(result.removedParameters.size)
        ClipboardMonitor.reportCleaned()

        if (app.preferences.hapticFeedbackEnabled.first()) vibrate(context)
        if (app.preferences.showToastEnabled.first()) {
            val count = result.removedParameters.size
            val msg = if (count > 0) {
                "SaneLink: removed $count tracker${if (count > 1) "s" else ""}"
            } else {
                "SaneLink: clean link copied"
            }
            Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
        return Outcome.CLEANED
    }

    private fun vibrate(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
            // Vibration is non-critical
        }
    }
}
