package com.sanelink.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sanelink.app.core.clipboard.CopySignalDetector
import com.sanelink.app.core.clipboard.CopySignalDetector.EventKind

/**
 * Hosts [ClipboardMonitor] and feeds it "the user probably just copied something" signals.
 *
 * Accessibility services cannot read the clipboard in the background on Android 10+ (that
 * was why the previous implementation never cleaned anything). What the binding does give
 * us is a process the system keeps alive and an exemption from background-activity-start
 * limits, so the monitor can briefly open [ClipboardReaderActivity] to read with focus.
 */
class ClipboardAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ClipboardMonitor.start(this)
    }

    @Suppress("DEPRECATION") // TYPE_ANNOUNCEMENT
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val kind = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> EventKind.CLICK
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> EventKind.LONG_CLICK
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> EventKind.TOAST
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> EventKind.WINDOW_STATE
            // Deprecated for new announcers, but SystemUI's "Copied" overlay still uses it.
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> EventKind.ANNOUNCEMENT
            else -> return
        }
        // Notification events carry a Notification parcelable; toasts don't.
        if (kind == EventKind.TOAST && event.parcelableData != null) return
        // Skip the noisy window event fired by our own invisible reader.
        if (event.className == ClipboardReaderActivity::class.java.name) return

        val texts = collectTexts(event, kind)
        val isCopy = CopySignalDetector.isCopySignal(
            kind = kind,
            packageName = event.packageName?.toString(),
            className = event.className?.toString(),
            texts = texts,
            ownPackageName = packageName
        )
        if (isCopy) {
            // Give the source app a moment to actually put the data on the clipboard.
            ClipboardMonitor.requestFocusedRead(delayMillis = COPY_SETTLE_DELAY_MS)
        }
    }

    private fun collectTexts(event: AccessibilityEvent, kind: EventKind): List<CharSequence> {
        val texts = mutableListOf<CharSequence>()
        event.text.filterNotNull().let(texts::addAll)
        event.contentDescription?.let(texts::add)
        if ((kind == EventKind.CLICK || kind == EventKind.LONG_CLICK) && texts.isEmpty()) {
            // Icon buttons or containers: look at the clicked node and its first children.
            event.source?.let { collectNodeTexts(it, texts, depth = 0) }
        }
        return texts
    }

    private fun collectNodeTexts(node: AccessibilityNodeInfo, out: MutableList<CharSequence>, depth: Int) {
        if (out.size >= MAX_NODE_TEXTS) return
        node.text?.let(out::add)
        node.contentDescription?.let(out::add)
        if (depth >= MAX_NODE_DEPTH) return
        for (i in 0 until minOf(node.childCount, MAX_NODE_TEXTS)) {
            val child = node.getChild(i) ?: continue
            collectNodeTexts(child, out, depth + 1)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        ClipboardMonitor.stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ClipboardMonitor.stop()
        super.onDestroy()
    }

    private companion object {
        const val COPY_SETTLE_DELAY_MS = 250L
        const val MAX_NODE_DEPTH = 2
        const val MAX_NODE_TEXTS = 8
    }
}
