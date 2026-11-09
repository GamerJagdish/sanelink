package com.sanelink.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_PROCESS_TEXT && intent.type == "text/plain") {
            val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

            if (!selectedText.isNullOrBlank()) {
                val app = SaneLinkApp.instance
                val sanitizer = app.repository.getSanitizer()
                val result = sanitizer.sanitize(selectedText)
                val cleanText = result.cleanText

                if (!isReadOnly) {
                    // Editable text box: replace selected text directly in place
                    val resultIntent = Intent()
                    resultIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, cleanText)
                    setResult(RESULT_OK, resultIntent)
                } else {
                    // Read-only text: copy cleaned text to clipboard
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("sanitized_url", cleanText)
                    cm?.setPrimaryClip(clip)
                }

                // Record stats and trigger haptic feedback if modified
                CoroutineScope(Dispatchers.IO).launch {
                    if (result.wasModified) {
                        app.preferences.recordSanitization(result.removedParameters.size)
                    }
                    if (app.preferences.hapticFeedbackEnabled.first()) {
                        triggerHaptic()
                    }
                }

                // Show feedback toast if enabled
                CoroutineScope(Dispatchers.Main).launch {
                    if (app.preferences.showToastEnabled.first()) {
                        val count = result.removedParameters.size
                        val msg = if (result.wasModified) {
                            if (!isReadOnly) {
                                if (count > 0) "Link cleaned in place! (Removed $count tracker${if (count > 1) "s" else ""})"
                                else "Link cleaned in place!"
                            } else {
                                if (count > 0) "Cleaned & copied! (Removed $count tracker${if (count > 1) "s" else ""})"
                                else "Clean link copied to clipboard"
                            }
                        } else {
                            if (!isReadOnly) "Link is already clean!"
                            else "Link is already clean! Copied to clipboard."
                        }
                        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        finish()
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (_: Exception) {
            // Ignore if vibration unavailable
        }
    }
}
