package org.quran.app.model

data class VerseTranslation(
    val editionId: String,
    val verseId: VerseId,
    /** Verbatim source text. This is a translation of the meanings, not Quran Arabic. */
    val text: String,
    val footnotes: String,
)
