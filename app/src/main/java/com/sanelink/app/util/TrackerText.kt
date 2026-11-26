package com.sanelink.app.util

/** "1 tracker", "3 trackers". */
fun trackersLabel(count: Int): String = if (count == 1) "1 tracker" else "$count trackers"
