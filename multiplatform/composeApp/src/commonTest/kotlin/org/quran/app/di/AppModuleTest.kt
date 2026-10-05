package org.quran.app.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication
import org.quran.app.data.BundledQuranRepository
import org.quran.app.data.StoredProgressRepository
import org.quran.app.domain.SettingsStore
import org.quran.app.model.StudyProgress
import org.quran.app.model.VerseId

class AppModuleTest {
    private class MemorySettings : SettingsStore {
        private val values = mutableMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun set(key: String, value: String) {
            values[key] = value
        }
    }

    @Test
    fun resolvesQuranAndProgressRepositories() {
        val application = koinApplication { modules(appModule(MemorySettings())) }

        try {
            assertTrue(application.koin.get<BundledQuranRepository>().chapters().isNotEmpty())
            assertTrue(application.koin.get<StoredProgressRepository>().read() == StudyProgress())
        } finally {
            application.close()
        }
    }

    @Test
    fun resolvedProgressRepositoryPersistsState() {
        val settings = MemorySettings()
        val application = koinApplication { modules(appModule(settings)) }

        try {
            val expected = StudyProgress(lastRead = VerseId(2, 286), childMode = true)
            application.koin.get<StoredProgressRepository>().save(expected)
            assertEquals(expected, application.koin.get<StoredProgressRepository>().read())
        } finally {
            application.close()
        }
    }
}
