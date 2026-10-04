package org.quran.app.model

/** Owned completed file metadata; no filesystem path is accepted from the UI. */
data class RecitationDownload(val reciterId: String, val verseId: VerseId, val sizeBytes: Long)
