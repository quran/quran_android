package org.quran.app.domain

import org.quran.app.model.ReadingPreferences

interface ReadingPreferencesStore {
    fun read(): ReadingPreferences
    fun save(preferences: ReadingPreferences)
}
