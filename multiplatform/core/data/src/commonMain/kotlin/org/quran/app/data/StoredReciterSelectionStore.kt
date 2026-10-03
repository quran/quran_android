package org.quran.app.data

import org.quran.app.domain.ReciterSelectionStore
import org.quran.app.domain.SettingsStore

class StoredReciterSelectionStore(private val settings: SettingsStore) : ReciterSelectionStore {
    override fun selectedReciterId(): String = settings.get(KEY)
        ?.takeIf { id -> EveryAyahCatalog.reciters.any { it.id == id } }
        ?: EveryAyahCatalog.DEFAULT_RECITER_ID
    override fun select(id: String) {
        EveryAyahCatalog.requireReciter(id)
        settings.set(KEY, id)
    }
    private companion object { const val KEY = "recitation.selected-reciter.v1" }
}
