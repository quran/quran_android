package org.quran.app.memorization

import org.quran.app.model.VerseId

data class RecitationQueueState(
    val reciterId: String? = null,
    val verses: List<VerseId> = emptyList(),
    val cachedCount: Int = 0,
    val completedDownloads: Int = 0,
    val isDownloading: Boolean = false,
    val failed: Boolean = false,
)
