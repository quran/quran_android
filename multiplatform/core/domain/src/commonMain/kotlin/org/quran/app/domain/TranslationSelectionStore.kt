package org.quran.app.domain

/** Persists a translation edition choice independently from interface language and reading progress. */
interface TranslationSelectionStore {
    fun selectedEditionId(): String?
    fun selectEdition(id: String?)
}
