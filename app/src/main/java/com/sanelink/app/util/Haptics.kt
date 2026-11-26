package com.sanelink.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sanelink.app.SaneLinkApp
import kotlinx.coroutines.flow.first

/** Short, subtle "click" vibration used across SaneLink. Silently does nothing if unavailable. */
fun Context.performClickHaptic() {
    try {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) {
        // Vibration is non-critical
    }
}

/** [performClickHaptic], but only when the user has haptic feedback enabled. */
suspend fun Context.performClickHapticIfEnabled() {
    if (SaneLinkApp.instance.preferences.hapticFeedbackEnabled.first()) performClickHaptic()
}
