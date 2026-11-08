package com.sanelink.app.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.quicksettings.TileService
import android.widget.Toast
import com.sanelink.app.SaneLinkApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SaneLinkTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm == null || !cm.hasPrimaryClip()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val text = clip.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "No text in clipboard", Toast.LENGTH_SHORT).show()
            return
        }

        val app = SaneLinkApp.instance
        val sanitizer = app.repository.getSanitizer()
        val result = sanitizer.sanitize(text)

        if (result.wasModified) {
            cm.setPrimaryClip(ClipData.newPlainText("sanitized_url", result.cleanText))
            CoroutineScope(Dispatchers.IO).launch {
                app.preferences.recordSanitization(result.removedParameters.size)
            }
            triggerHaptic()
            val count = result.removedParameters.size
            Toast.makeText(
                this,
                "Cleaned! Removed $count tracker${if (count > 1) "s" else ""}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Link in clipboard is already clean!", Toast.LENGTH_SHORT).show()
        }
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
                vibrator?.vibrate(30)
            }
        } catch (_: Exception) {
            // Ignore if vibration unavailable
        }
    }
}
