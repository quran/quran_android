package org.quran.app.domain

import org.quran.app.model.VerseId

data class RepeatState(
    val currentVerse: VerseId,
    val completedRepetitions: Int,
    val complete: Boolean,
)
