package com.sanelink.app.core.clipboard

import com.sanelink.app.core.clipboard.CopySignalDetector.EventKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopySignalDetectorTest {

    private val own = "com.gamerjagdish.sanelink"

    private fun signal(
        kind: EventKind,
        vararg texts: String,
        pkg: String = "com.instagram.android",
        cls: String? = null
    ) = CopySignalDetector.isCopySignal(kind, pkg, cls, texts.toList(), own)

    @Test
    fun `copy buttons and menu items are detected`() {
        assertTrue(signal(EventKind.CLICK, "Copy link"))
        assertTrue(signal(EventKind.CLICK, "Copy"))
        assertTrue(signal(EventKind.LONG_CLICK, "Copy URL"))
        assertTrue(signal(EventKind.CLICK, "Link kopieren"))
        assertTrue(signal(EventKind.CLICK, "复制链接"))
    }

    @Test
    fun `ordinary clicks and long paragraphs are ignored`() {
        assertFalse(signal(EventKind.CLICK, "Like"))
        assertFalse(signal(EventKind.CLICK, "Share"))
        assertFalse(signal(EventKind.CLICK, "Copyright 2026"))
        assertFalse(signal(EventKind.CLICK, "This long caption mentions how to copy a recipe from a book at home"))
        assertFalse(signal(EventKind.CLICK))
    }

    @Test
    fun `copy words only match as whole words`() {
        assertFalse(signal(EventKind.CLICK, "Photocopier"))
        assertFalse(signal(EventKind.CLICK, "Saline"))
        assertFalse(signal(EventKind.TOAST, "Clipboard cleared"))
        assertTrue(signal(EventKind.CLICK, "Copy link"))
        assertTrue(signal(EventKind.CLICK, "Salin tautan"))
    }

    @Test
    fun `copied toasts and announcements are detected`() {
        assertTrue(signal(EventKind.TOAST, "Link copied to clipboard"))
        assertTrue(signal(EventKind.ANNOUNCEMENT, "Copied"))
        assertFalse(signal(EventKind.TOAST, "Message sent"))
    }

    @Test
    fun `android 12+ paste notices are not copy signals`() {
        // Shown by the framework whenever any app reads the clipboard, including our own reader
        assertFalse(signal(EventKind.TOAST, "Chrome pasted from your clipboard", pkg = "android"))
        assertFalse(signal(EventKind.TOAST, "SaneLink pasted from your clipboard", pkg = "android"))
        assertFalse(signal(EventKind.TOAST, "Notes pasted from your clipboard", pkg = "com.example.notes"))
        // A regular app toast about copying still counts
        assertTrue(signal(EventKind.TOAST, "Copied to clipboard", pkg = "com.example.notes"))
    }

    @Test
    fun `android 13 clipboard overlay from system ui is detected`() {
        assertTrue(signal(EventKind.WINDOW_STATE, pkg = "com.android.systemui", cls = "com.android.systemui.clipboardoverlay.ClipboardOverlayWindow"))
        assertTrue(signal(EventKind.WINDOW_STATE, "Copied", pkg = "com.android.systemui", cls = "android.widget.FrameLayout"))
        assertFalse(signal(EventKind.WINDOW_STATE, "Notification shade", pkg = "com.android.systemui", cls = "android.widget.FrameLayout"))
        // Other apps' window changes are not copy signals
        assertFalse(signal(EventKind.WINDOW_STATE, "Copied", cls = "android.app.Activity"))
    }

    @Test
    fun `events from SaneLink itself never trigger`() {
        assertFalse(signal(EventKind.CLICK, "Copy", pkg = own))
        assertFalse(signal(EventKind.TOAST, "Clean link copied", pkg = own))
    }
}
