package com.sanelink.app.core.history

import kotlinx.serialization.Serializable

/** Where a cleaned link came from. */
@Serializable
enum class CleanSource(val label: String) {
    AUTO("Copied"),
    TILE("Quick Settings"),
    SHARE("Shared"),
    TEXT_SELECTION("Text selection")
}

/** One cleaned link, kept only on this device. */
@Serializable
data class RecentLink(
    val id: String = java.util.UUID.randomUUID().toString(),
    val original: String,
    val clean: String,
    val removed: List<String> = emptyList(),
    val source: CleanSource,
    val timestamp: Long = System.currentTimeMillis()
)

/** Pure list handling for the recent links log. */
object RecentLinks {

    /** "Keep" choices offered in Settings. [UNLIMITED] keeps everything; [OFF] keeps nothing. */
    const val OFF = 0
    const val UNLIMITED = -1
    const val DEFAULT_LIMIT = 20
    val LIMIT_CHOICES = listOf(OFF, 10, 20, 50, UNLIMITED)

    /**
     * Adds [entry] at the top (newest first) and applies [limit]. Cleaning the same original
     * link again moves it to the top instead of adding a duplicate.
     */
    fun add(current: List<RecentLink>, entry: RecentLink, limit: Int): List<RecentLink> {
        if (limit == OFF) return emptyList()
        val withoutRepeat = current.filterNot { it.original == entry.original }
        return trim(listOf(entry) + withoutRepeat, limit)
    }

    /** Keeps the newest [limit] entries ([UNLIMITED] keeps all, [OFF] keeps none). */
    fun trim(current: List<RecentLink>, limit: Int): List<RecentLink> = when {
        limit == UNLIMITED -> current
        limit <= OFF -> emptyList()
        else -> current.take(limit)
    }
}
