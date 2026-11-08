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
import kotlinx.coroutines.launch

class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val app = SaneLinkApp.instance
                val sanitizer = app.repository.getSanitizer()
                val result = sanitizer.sanitize(sharedText)

                val textToCopy = if (result.wasModified) result.cleanText else sharedText

                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("sanitized_url", textToCopy)
                cm?.setPrimaryClip(clip)

                if (result.wasModified) {
                    CoroutineScope(Dispatchers.IO).launch {
                        app.preferences.recordSanitization(result.removedParameters.size)
                    }
                    val count = result.removedParameters.size
                    val msg = if (count > 0) {
                        "Cleaned & copied! (Removed $count tracker${if (count > 1) "s" else ""})"
                    } else {
                        "Cleaned & copied to clipboard"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Link is already clean! Copied.", Toast.LENGTH_SHORT).show()
                }

                triggerHaptic()
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
