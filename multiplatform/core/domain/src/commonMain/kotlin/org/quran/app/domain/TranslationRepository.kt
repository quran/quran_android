package org.quran.app.domain

import org.quran.app.model.TranslationEdition
import org.quran.app.model.VerseTranslation

/** Loads licensed translation editions and complete surahs independently of platform APIs. */
interface TranslationRepository {
    suspend fun editions(languageCode: String? = null): List<TranslationEdition>
    suspend fun verses(edition: TranslationEdition, surah: Int): List<VerseTranslation>
}
