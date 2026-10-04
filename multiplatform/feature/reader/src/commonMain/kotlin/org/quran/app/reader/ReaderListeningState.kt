package org.quran.app.reader

import org.quran.app.model.VerseId

/** Presentation state supplied by the host that owns the recitation and player lifecycles. */
data class ReaderListeningState(
    val verseId: VerseId? = null,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val isReady: Boolean = false,
    val failed: Boolean = false,
)
