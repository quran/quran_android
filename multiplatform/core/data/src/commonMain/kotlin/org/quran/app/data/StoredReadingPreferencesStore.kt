package org.quran.app.data

import org.quran.app.domain.ReadingPreferencesStore
import org.quran.app.domain.SettingsStore
import org.quran.app.model.ReadingPreferences
import org.quran.app.model.ReadingTextSize

/** Additive key: changing typography cannot rewrite progress.v1 or translation selection. */
class StoredReadingPreferencesStore(private val settings: SettingsStore) : ReadingPreferencesStore {
    override fun read(): ReadingPreferences {
        val fields = settings.get(KEY)?.split('|') ?: return ReadingPreferences()
        if (fields.size != 3 || fields[0] != "1") return ReadingPreferences()
        return ReadingPreferences(size(fields[1]), size(fields[2]))
    }
    override fun save(preferences: ReadingPreferences) {
        settings.set(KEY, "1|${preferences.arabicTextSize.name}|${preferences.translationTextSize.name}")
    }
    private fun size(value: String): ReadingTextSize = ReadingTextSize.entries.firstOrNull { it.name == value }
        ?: ReadingTextSize.DEFAULT
    private companion object { const val KEY = "reading.preferences.v1" }
}
