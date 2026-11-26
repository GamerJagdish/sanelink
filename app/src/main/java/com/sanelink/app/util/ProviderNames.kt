package com.sanelink.app.util

import java.util.concurrent.ConcurrentHashMap

private val formattedNameCache = ConcurrentHashMap<String, String>()

/** Turns a ClearURLs provider id ("amazon_search") into a display name ("Amazon Search"). */
fun formatProviderName(key: String): String {
    return formattedNameCache.getOrPut(key) {
        key.split("_", "-", " ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
