package org.quran.app.model

/** A named translation of the meanings; kept separate from canonical Arabic text. */
data class TranslationEdition(
    val id: String,
    val languageCode: String,
    val languageName: String,
    val title: String,
    val translator: String,
    val version: String,
    val lastUpdatedEpochSeconds: Long,
    val direction: TextDirection,
    val publisher: String = "QuranEnc.com",
    val sourceUrl: String = "https://quranenc.com/en/home/api",
)
