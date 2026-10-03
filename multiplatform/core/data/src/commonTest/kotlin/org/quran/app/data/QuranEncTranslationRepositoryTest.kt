package org.quran.app.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class QuranEncTranslationRepositoryTest {
    @Test
    fun invalidSurahResponseIsRejectedAndNeverCached() = runTest {
        val store = MemorySettingsStore()
        val client = FakeTranslationHttpClient { TranslationTestFixtures.surahJson(count = 6) }
        val repository = QuranEncTranslationRepository(client, store)

        assertFails { repository.verses(TranslationTestFixtures.edition(), 1) }
        assertNull(store.get("translation.cache.v1.english_saheeh.1.1.2.1"))
    }

    @Test
    fun validatedSurahCacheSupportsOfflineReading() = runTest {
        val store = MemorySettingsStore()
        val online = QuranEncTranslationRepository(
            FakeTranslationHttpClient { TranslationTestFixtures.surahJson() },
            store,
        )
        val expected = online.verses(TranslationTestFixtures.edition(), 1)
        val offline = QuranEncTranslationRepository(FakeTranslationHttpClient { error("offline") }, store)

        assertEquals(expected, offline.verses(TranslationTestFixtures.edition(), 1))
    }

    @Test
    fun refreshesCatalogUsingConfiguredEndpointAndCachesForOfflineUse() = runTest {
        val store = MemorySettingsStore()
        val client = FakeTranslationHttpClient { TranslationTestFixtures.CATALOG_JSON }
        val online = QuranEncTranslationRepository(client, store)
        val expected = online.editions("en")
        val offline = QuranEncTranslationRepository(FakeTranslationHttpClient { error("offline") }, store)

        assertEquals(listOf("https://quranenc.com/api/v1/translations/list/en?localization=en"), client.requestedUrls)
        assertEquals(expected, offline.editions("en"))
    }
}
