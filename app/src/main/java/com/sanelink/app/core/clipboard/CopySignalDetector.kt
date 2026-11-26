package com.sanelink.app.core.clipboard

import java.util.Locale

/**
 * Heuristics that decide whether an accessibility event probably means "the user just
 * copied something". Used when the logcat signal is unavailable (no `READ_LOGS` grant),
 * and as a second, faster signal when it is.
 *
 * Kept free of Android types so it can be unit tested.
 */
object CopySignalDetector {

    /** Mirrors the `AccessibilityEvent.TYPE_*` constants we subscribe to. */
    enum class EventKind { CLICK, LONG_CLICK, TOAST, WINDOW_STATE, ANNOUNCEMENT, OTHER }

    private const val SYSTEM_UI = "com.android.systemui"

    /**
     * The framework itself ("android") shows "<App> pasted from your clipboard" toasts on
     * Android 12+ whenever any app reads the clipboard, including SaneLink's own reader. Those
     * are paste notices, not copies, and reacting to them would re-open the reader on every paste.
     */
    private const val FRAMEWORK = "android"

    /** Labels of buttons / menu items that copy something ("Copy", "Copy link", ...). */
    private val copyActionWords = listOf(
        "copy",          // en
        "copiar",        // es / pt
        "copier",        // fr
        "kopieren",      // de
        "copia",         // it
        "kopiuj",        // pl
        "kopyala",       // tr
        "kopiëren",      // nl
        "копировать",    // ru
        "скопировать",   // ru
        "копіювати",     // uk
        "复制",           // zh
        "複製",           // zh-TW
        "コピー",          // ja
        "복사",           // ko
        "salin",         // id / ms
        "sao chép",      // vi
        "कॉपी",          // hi
    )

    /** Confirmation texts shown after a copy ("Link copied", "Copied to clipboard"). */
    private val copiedConfirmationWords = listOf(
        "copied", "clipboard",
        "copiado", "copié", "kopiert", "copiato", "skopiowano", "kopyalandı", "gekopieerd",
        "скопировано", "скопійовано", "已复制", "已複製", "コピーしました", "복사됨",
        "disalin", "đã sao chép", "कॉपी किया",
    )

    /** Paste notices ("Chrome pasted from your clipboard"), which also mention the clipboard. */
    private val pasteNoticeWords = listOf("pasted", "pegó", "a collé", "eingefügt", "incollato", "colou")

    /** Texts that contain a copy word but are clearly not a copy action. */
    private val falsePositiveWords = listOf("copyright", "copies", "cleared")

    fun isCopySignal(
        kind: EventKind,
        packageName: String?,
        className: String?,
        texts: List<CharSequence>,
        ownPackageName: String
    ): Boolean {
        if (packageName == ownPackageName) return false
        val joined = texts.joinToString(" ").lowercase(Locale.ROOT).trim()

        return when (kind) {
            CopySignalDetector.EventKind.CLICK,
            CopySignalDetector.EventKind.LONG_CLICK -> isCopyActionLabel(joined)

            // Toasts / announcements like "Link copied to clipboard"
            CopySignalDetector.EventKind.TOAST,
            CopySignalDetector.EventKind.ANNOUNCEMENT ->
                packageName != FRAMEWORK &&
                    !containsAny(joined, pasteNoticeWords) &&
                    !containsAny(joined, falsePositiveWords) &&
                    containsAny(joined, copiedConfirmationWords)

            // Android 13+ system clipboard overlay appearing after any copy
            CopySignalDetector.EventKind.WINDOW_STATE ->
                packageName == SYSTEM_UI && (
                    className.orEmpty().lowercase(Locale.ROOT).contains("clipboard") ||
                        containsAny(joined, copiedConfirmationWords)
                    )

            CopySignalDetector.EventKind.OTHER -> false
        }
    }

    /**
     * Short labels only: a click on a long paragraph that happens to contain "copy" is not
     * a copy button.
     */
    internal fun isCopyActionLabel(label: String): Boolean {
        if (label.isEmpty() || label.length > 40) return false
        if (containsAny(label, falsePositiveWords)) return false
        return containsAny(label, copyActionWords) || containsAny(label, copiedConfirmationWords)
    }

    private fun containsAny(text: String, words: List<String>): Boolean =
        words.any { containsWord(text, it) }

    /**
     * Whole-word match for words in alphabetic scripts ("copier" must not match
     * "photocopier", "salin" must not match "saline"). Scripts written without spaces
     * (Chinese, Japanese, Korean, Hindi) fall back to a plain substring match.
     */
    private fun containsWord(text: String, word: String): Boolean {
        if (word.any { Character.UnicodeScript.of(it.code) in UNSPACED_SCRIPTS }) return text.contains(word)
        var from = 0
        while (true) {
            val idx = text.indexOf(word, from)
            if (idx < 0) return false
            val before = text.getOrNull(idx - 1)
            val after = text.getOrNull(idx + word.length)
            if ((before == null || !before.isLetter()) && (after == null || !after.isLetter())) return true
            from = idx + 1
        }
    }

    private val UNSPACED_SCRIPTS = setOf(
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.DEVANAGARI,
    )
}
