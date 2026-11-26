package com.sanelink.app.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import kotlinx.coroutines.launch

/**
 * Invisible 1×1 activity that exists only to hold input focus for a moment.
 *
 * Android 10+ lets an app read the clipboard only while one of its windows is focused.
 * This activity takes focus, cleans the clipboard via [ClipboardCleaner] and closes itself,
 * usually within ~100 ms. It does not intercept touches outside its single pixel.
 */
class ClipboardReaderActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var handled = false

    private val giveUp = Runnable { close() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableTransitions()
        window.apply {
            setLayout(1, 1)
            setGravity(Gravity.TOP or Gravity.START)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            setDimAmount(0f)
        }
        // If focus never arrives (e.g. screen locked), don't linger.
        handler.postDelayed(giveUp, FOCUS_TIMEOUT_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true
        handler.removeCallbacks(giveUp)
        lifecycleScope.launch {
            try {
                ClipboardCleaner.cleanPrimaryClip(
                    this@ClipboardReaderActivity,
                    manual = intent.getBooleanExtra(EXTRA_MANUAL, false)
                )
            } finally {
                close()
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        super.onDestroy()
    }

    private fun close() {
        if (isFinishing) return
        finishAndRemoveTask()
        disableTransitions()
    }

    @Suppress("DEPRECATION")
    private fun disableTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val FOCUS_TIMEOUT_MS = 1_500L
        private const val EXTRA_MANUAL = "com.sanelink.app.extra.MANUAL_CLEAN"

        /** @param manual true for an explicit user request (Quick Settings tile). */
        fun createIntent(context: Context, manual: Boolean = false): Intent =
            Intent(context, ClipboardReaderActivity::class.java)
                .putExtra(EXTRA_MANUAL, manual)
                .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            )
    }
}
