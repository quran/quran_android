package org.quran.app

import org.quran.app.model.TranslationEdition
import org.quran.app.model.VerseId
import org.quran.app.model.VerseTranslation

internal data class ReaderTranslationState(
    val edition: TranslationEdition? = null,
    val verses: Map<VerseId, VerseTranslation> = emptyMap(),
    val isLoading: Boolean = false,
    val isCached: Boolean = false,
    val isOlder: Boolean = false,
    val failed: Boolean = false,
)
