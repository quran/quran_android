package org.quran.app.data

/** Internal filesystem metadata; platform adapters enumerate regular owned files only. */
data class RecitationCacheEntry(val key: String, val sizeBytes: Long)
