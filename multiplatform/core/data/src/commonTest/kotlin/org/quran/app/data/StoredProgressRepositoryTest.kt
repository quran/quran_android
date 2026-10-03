package org.quran.app.data

import org.quran.app.model.*
import org.quran.app.domain.SettingsStore
import kotlin.test.*

class StoredProgressRepositoryTest {
    private class MemorySettings : SettingsStore {
        val entries = mutableMapOf<String, String>()
        override fun get(key: String): String? = entries[key]
        override fun set(key: String, value: String) { entries[key] = value }
    }

    @Test fun emptyStorageReturnsSafeDefaults() {
        assertEquals(StudyProgress(), StoredProgressRepository(MemorySettings()).read())
    }

    @Test fun learningStatesAndLanguageSurviveRepositoryRecreation() {
        val store = MemorySettings()
        val expected = StudyProgress(
            lastRead = VerseId(2, 286),
            memorized = setOf(VerseId(1, 1), VerseId(114, 6)),
            bookmarks = setOf(VerseId(2, 10)),
            language = AppLanguage.ARABIC,
            childMode = true,
        )
        StoredProgressRepository(store).save(expected)
        assertEquals(expected, StoredProgressRepository(store).read())
        assertEquals(1, store.entries.size, "Progress should be saved as a single atomic settings value")
    }

    @Test fun corruptAndFutureStorageCannotCreateInvalidProgress() {
        val store = MemorySettings()
        for (value in listOf("broken", "", "v999|999:999|ar|true", "{\"lastRead\":\"1:8\"}")) {
            store.entries["progress.v1"] = value
            assertEquals(StudyProgress(), StoredProgressRepository(store).read())
        }
    }

    @Test fun savingNewProgressReplacesRemovedBookmarks() {
        val store = MemorySettings()
        val repo = StoredProgressRepository(store)
        repo.save(StudyProgress(bookmarks = setOf(VerseId(1, 1))))
        repo.save(StudyProgress())
        assertTrue(StoredProgressRepository(store).read().bookmarks.isEmpty())
    }
}
