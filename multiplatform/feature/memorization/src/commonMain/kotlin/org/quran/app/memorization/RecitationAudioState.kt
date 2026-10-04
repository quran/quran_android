package org.quran.app.memorization

import org.quran.app.model.VerseId

data class RecitationAudioState(
    val reciterId: String? = null,
    val verseId: VerseId? = null,
    val cachedUri: String? = null,
    val isDownloading: Boolean = false,
    val failed: Boolean = false,
)
