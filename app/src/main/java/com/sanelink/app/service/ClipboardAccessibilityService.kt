package com.sanelink.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.sanelink.app.SaneLinkApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ClipboardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var clipboardManager: ClipboardManager? = null
    private var lastSanitizedClip: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        serviceScope.launch {
            val app = SaneLinkApp.instance
            val autoEnabled = app.preferences.autoSanitizeEnabled.first()
            if (!autoEnabled) return@launch

            checkAndSanitizeClipboard()
        }
    }

    private suspend fun checkAndSanitizeClipboard() {
        val cm = clipboardManager ?: return
        if (!cm.hasPrimaryClip()) return

        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return

        val text = clip.getItemAt(0)?.text?.toString() ?: return
        if (text.isBlank() || text == lastSanitizedClip) return

        val app = SaneLinkApp.instance
        val sanitizer = app.repository.getSanitizer()
        val result = sanitizer.sanitize(text)

        if (result.wasModified && result.cleanText != text) {
            lastSanitizedClip = result.cleanText

            // Set clean URL back to clipboard
            val newClip = ClipData.newPlainText("sanitized_url", result.cleanText)
            cm.setPrimaryClip(newClip)

            // Update stats
            app.preferences.recordSanitization(result.removedParameters.size)

            // Subtle haptic feedback
            if (app.preferences.hapticFeedbackEnabled.first()) {
                triggerHaptic()
            }

            // Discreet toast
            if (app.preferences.showToastEnabled.first()) {
                val count = result.removedParameters.size
                val msg = if (count > 0) {
                    "SaneLink: Removed $count tracker${if (count > 1) "s" else ""}"
                } else {
                    "SaneLink: Clean link copied"
                }
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            }
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
            // Non-critical if vibration fails
        }
    }

    override fun onInterrupt() {
        // Required lifecycle method
    }
}
