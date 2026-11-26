package com.sanelink.app

import com.sanelink.app.util.trackersLabel
import com.sanelink.app.service.ClipboardCleaner
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sanelink.app.service.ClipboardMonitor
import com.sanelink.app.util.performClickHapticIfEnabled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Share target: cleans the shared text, copies it to the clipboard and closes. */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Shared text may be rich (Spanned) or typed as text/plain; charset=utf-8
        val sharedText = intent
            ?.takeIf { it.action == Intent.ACTION_SEND && it.type?.startsWith("text/") == true }
            ?.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
        if (sharedText.isNullOrBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                handle(sharedText)
            } finally {
                finish()
            }
        }
    }

    private suspend fun handle(sharedText: String) {
        val app = SaneLinkApp.instance
        // Wait for the user's custom rules and patches (matters right after a cold start)
        val result = app.repository.awaitSanitizer().sanitize(sharedText)
        val textToCopy = if (result.wasModified) result.cleanText else sharedText

        ClipboardCleaner.writeOwnClip(this, textToCopy)

        if (result.wasModified) app.preferences.recordSanitization(result.removedParameters.size)
        performClickHapticIfEnabled()

        if (app.preferences.showToastEnabled.first()) {
            val count = result.removedParameters.size
            val msg = when {
                !result.wasModified -> "Link is already clean! Copied."
                count > 0 -> "Cleaned & copied! (Removed ${trackersLabel(count)})"
                else -> "Cleaned & copied to clipboard"
            }
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
