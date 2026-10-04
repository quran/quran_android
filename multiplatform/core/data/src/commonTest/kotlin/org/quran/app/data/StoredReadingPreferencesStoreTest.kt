package org.quran.app.data

import kotlin.test.*
import org.quran.app.model.*

class StoredReadingPreferencesStoreTest {
    @Test fun missingStoragePreservesDefaultTypography() {
        assertEquals(ReadingPreferences(), StoredReadingPreferencesStore(MemorySettingsStore()).read())
    }

    @Test fun independentSelectionsSurviveRecreationInOneAtomicValue() {
        val settings = MemorySettingsStore()
        val store = StoredReadingPreferencesStore(settings)
        val expected = ReadingPreferences(ReadingTextSize.LARGE, ReadingTextSize.DEFAULT)
        store.save(expected)
        assertEquals("1|LARGE|DEFAULT", settings.get("reading.preferences.v1"))
        assertEquals(expected, StoredReadingPreferencesStore(settings).read())
        val other = ReadingPreferences(ReadingTextSize.DEFAULT, ReadingTextSize.LARGE)
        store.save(other)
        assertEquals(other, StoredReadingPreferencesStore(settings).read())
    }

    @Test fun addingTypographyNeverRewritesExistingProgress() {
        val settings = MemorySettingsStore()
        val progress = StudyProgress(
            lastRead = VerseId(2, 45), bookmarks = setOf(VerseId(18, 75)),
            memorized = setOf(VerseId(114, 1)), language = AppLanguage.ARABIC, childMode = true,
        )
        StoredProgressRepository(settings).save(progress)
        val original = settings.get("progress.v1")
        assertEquals(ReadingPreferences(), StoredReadingPreferencesStore(settings).read())
        StoredReadingPreferencesStore(settings).save(ReadingPreferences(ReadingTextSize.LARGE, ReadingTextSize.LARGE))
        assertEquals(original, settings.get("progress.v1"))
        assertEquals(progress, StoredProgressRepository(settings).read())
    }

    @Test fun corruptAndUnknownVersionsReturnDefaultsAndInvalidSizeFallsBackIndependently() {
        val settings = MemorySettingsStore()
        val store = StoredReadingPreferencesStore(settings)
        for (value in listOf("", "broken", "2|LARGE|LARGE", "1|LARGE", "1|LARGE|DEFAULT|extra")) {
            settings.set("reading.preferences.v1", value)
            assertEquals(ReadingPreferences(), store.read())
        }
        settings.set("reading.preferences.v1", "1|UNKNOWN|LARGE")
        assertEquals(ReadingPreferences(ReadingTextSize.DEFAULT, ReadingTextSize.LARGE), store.read())
        settings.set("reading.preferences.v1", "1|LARGE|unknown")
        assertEquals(ReadingPreferences(ReadingTextSize.LARGE, ReadingTextSize.DEFAULT), store.read())
    }
}
