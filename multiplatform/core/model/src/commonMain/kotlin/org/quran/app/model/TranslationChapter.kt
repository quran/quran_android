package org.quran.app.model

/** A chapter and the edition metadata belonging to its actual downloaded wording. */
data class TranslationChapter(
    val edition: TranslationEdition,
    val verses: List<VerseTranslation>,
    val isCached: Boolean = false,
    /** The catalog has a later update, or a different version at the same update timestamp. */
    val isOlder: Boolean = false,
)
