package com.sanelink.app

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

/**
 * "Clean Link" in the text-selection menu. Replaces the selection in editable fields, or
 * copies the clean text for read-only text.
 */
class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent
            ?.takeIf { it.action == Intent.ACTION_PROCESS_TEXT }
            ?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?.toString()
        if (selectedText.isNullOrBlank()) {
            finish()
            return
        }
        val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

        lifecycleScope.launch {
            try {
                handle(selectedText, isReadOnly)
            } finally {
                finish()
            }
        }
    }

    private suspend fun handle(selectedText: String, isReadOnly: Boolean) {
        val app = SaneLinkApp.instance
        val result = app.repository.awaitSanitizer().sanitize(selectedText)

        if (!isReadOnly) {
            // Only replace the selection when something changed, so formatting isn't lost
            if (result.wasModified) {
                setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, result.cleanText))
            }
        } else {
            ClipboardMonitor.guard.recordOwnWrite(result.cleanText)
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("SaneLink", result.cleanText))
        }

        if (result.wasModified) app.preferences.recordSanitization(result.removedParameters.size)
        performClickHapticIfEnabled()

        if (app.preferences.showToastEnabled.first()) {
            val count = result.removedParameters.size
            val removed = if (count > 0) " (Removed $count tracker${if (count > 1) "s" else ""})" else ""
            val msg = when {
                result.wasModified && !isReadOnly -> "Link cleaned in place!$removed"
                result.wasModified -> "Cleaned & copied!$removed"
                !isReadOnly -> "Link is already clean!"
                else -> "Link is already clean! Copied to clipboard."
            }
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
