package org.quran.app.data

import org.quran.app.domain.SettingsStore
import org.quran.app.domain.TranslationSelectionStore

class StoredTranslationSelectionStore(
    private val settings: SettingsStore,
) : TranslationSelectionStore {
    override fun selectedEditionId(): String? = settings.get(KEY)
        ?.takeIf { it.matches(EDITION_ID_PATTERN) }

    override fun selectEdition(id: String?) {
        require(id == null || id.matches(EDITION_ID_PATTERN)) { "Invalid translation edition identifier" }
        settings.set(KEY, id.orEmpty())
    }

    private companion object {
        const val KEY = "translation.selected-edition.v1"
        val EDITION_ID_PATTERN = Regex("[a-z0-9_]+")
    }
}
