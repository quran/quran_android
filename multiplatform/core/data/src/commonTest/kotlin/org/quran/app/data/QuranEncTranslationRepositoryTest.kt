package org.quran.app.data

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class QuranEncTranslationRepositoryTest {
    @Test
    fun changedVersionWithUnchangedTimestampMarksTheCachedEditionAsOlder() = runTest {
        val store = MemorySettingsStore()
        val original = TranslationTestFixtures.edition()
        QuranEncTranslationRepository(FakeTranslationHttpClient { TranslationTestFixtures.surahJson() }, store)
            .chapter(original, 1)
        val current = original.copy(version = "2.0.0")

        val chapter = QuranEncTranslationRepository(FakeTranslationHttpClient { error("offline") }, store)
            .chapter(current, 1)

        assertEquals(original, chapter.edition)
        assertTrue(chapter.isOlder)
    }

    @Test
    fun cancellationPropagatesInsteadOfReturningOrReplacingCachedContent() = runTest {
        val store = MemorySettingsStore()
        val original = TranslationTestFixtures.edition()
        val expected = QuranEncTranslationRepository(FakeTranslationHttpClient { TranslationTestFixtures.surahJson() }, store)
            .chapter(original, 1)
        val repository = QuranEncTranslationRepository(
            FakeTranslationHttpClient { throw CancellationException("Reader disposed") }, store,
        )

        assertFailsWith<CancellationException> { repository.chapter(original.copy(version = "2.0.0"), 1) }
        assertFailsWith<CancellationException> { repository.editions() }
        val cached = QuranEncTranslationRepository(FakeTranslationHttpClient { error("offline") }, store)
            .chapter(original, 1)
        assertEquals(expected.edition, cached.edition)
        assertEquals(expected.verses, cached.verses)
    }

    @Test
    fun catalogUpgradeKeepsDownloadedChapterReadableWithItsOriginalAttributionOffline() = runTest {
        val store = MemorySettingsStore()
        val original = TranslationTestFixtures.edition()
        val online = QuranEncTranslationRepository(
            FakeTranslationHttpClient { url ->
                if (url.contains("translations/list")) TranslationTestFixtures.CATALOG_JSON
                else TranslationTestFixtures.surahJson(firstTranslation = "Original edition wording")
            },
            store,
        )
        val downloaded = online.chapter(original, 1)
        val updatedCatalog = TranslationTestFixtures.CATALOG_JSON
            .replace("1.1.2", "2.0.0")
            .replace("1773348325", "1773348326")
            .replace("Saheeh International", "Updated translator metadata")
        val catalogRefresh = QuranEncTranslationRepository(FakeTranslationHttpClient { updatedCatalog }, store)
        val current = catalogRefresh.editions().single()
        val offline = QuranEncTranslationRepository(FakeTranslationHttpClient { error("offline") }, store)

        val chapter = offline.chapter(current, 1)

        assertEquals(original, chapter.edition)
        assertEquals(downloaded.verses, chapter.verses)
        assertEquals("Original edition wording", chapter.verses.first().text)
        assertTrue(chapter.isCached)
        assertTrue(chapter.isOlder)
    }

    @Test
    fun successfulChapterRefreshReplacesOldCacheWithMatchingCurrentAttribution() = runTest {
        val store = MemorySettingsStore()
        val original = TranslationTestFixtures.edition()
        QuranEncTranslationRepository(FakeTranslationHttpClient { TranslationTestFixtures.surahJson() }, store)
            .chapter(original, 1)
        val current = original.copy(version = "2.0.0", lastUpdatedEpochSeconds = original.lastUpdatedEpochSeconds + 1)
        val refreshed = QuranEncTranslationRepository(
            FakeTranslationHttpClient { TranslationTestFixtures.surahJson(firstTranslation = "Updated edition wording") },
            store,
        ).chapter(current, 1)
        val offline = QuranEncTranslationRepository(FakeTranslationHttpClient { error("offline") }, store)
            .chapter(current, 1)

        assertEquals(current, refreshed.edition)
        assertFalse(refreshed.isCached)
        assertFalse(refreshed.isOlder)
        assertEquals(refreshed.edition, offline.edition)
        assertEquals(refreshed.verses, offline.verses)
        assertTrue(offline.isCached)
        assertFalse(offline.isOlder)
    }

    @Test
    fun incompleteRefreshDoesNotDestroyThePreviouslyValidatedEditionSnapshot() = runTest {
        val store = MemorySettingsStore()
        val original = TranslationTestFixtures.edition()
        val expected = QuranEncTranslationRepository(FakeTranslationHttpClient { TranslationTestFixtures.surahJson() }, store)
            .chapter(original, 1)
        val current = original.copy(version = "2.0.0", lastUpdatedEpochSeconds = original.lastUpdatedEpochSeconds + 1)
        val chapter = QuranEncTranslationRepository(
            FakeTranslationHttpClient { TranslationTestFixtures.surahJson(count = 6) },
            store,
        ).chapter(current, 1)

        assertEquals(original, chapter.edition)
        assertEquals(expected.verses, chapter.verses)
        assertTrue(chapter.isCached)
        assertTrue(chapter.isOlder)
    }

    @Test
    fun corruptOrMismatchedCacheMetadataCannotRelabelAnotherEdition() = runTest {
        val original = TranslationTestFixtures.edition()
        for (invalidSnapshot in listOf("not JSON", "{\"schema\":2}", "{}")) {
            val store = MemorySettingsStore()
            store.set("translation.chapter.v2.${original.id}.1", invalidSnapshot)
            val offline = QuranEncTranslationRepository(FakeTranslationHttpClient { error("offline") }, store)
            assertFails { offline.chapter(original, 1) }
        }
        val store = MemorySettingsStore()
        QuranEncTranslationRepository(FakeTranslationHttpClient { TranslationTestFixtures.surahJson() }, store)
            .chapter(original, 1)
        val key = "translation.chapter.v2.${original.id}.1"
        store.set(key, store.get(key)!!.replace(original.id, "another_edition"))
        val offline = QuranEncTranslationRepository(FakeTranslationHttpClient { error("offline") }, store)
        assertFails { offline.chapter(original, 1) }
    }

    @Test
    fun cacheRejectsUnsafeSourceLinkAndMismatchedChapterNumbering() = runTest {
        val original = TranslationTestFixtures.edition()
        for (tamper in listOf<(String) -> String>(
            { snapshot -> snapshot.replace("https://quranenc.com/en/home/api", "https://untrusted.invalid") },
            { snapshot -> snapshot.replace("\\\"sura\\\":\\\"1\\\"", "\\\"sura\\\":\\\"2\\\"") },
        )) {
            val store = MemorySettingsStore()
            QuranEncTranslationRepository(FakeTranslationHttpClient { TranslationTestFixtures.surahJson() }, store)
                .chapter(original, 1)
            val key = "translation.chapter.v2.${original.id}.1"
            val valid = store.get(key)!!
            val invalid = tamper(valid)
            assertTrue(invalid != valid, "The test must change the snapshot")
            store.set(key, invalid)
            val offline = QuranEncTranslationRepository(FakeTranslationHttpClient { error("offline") }, store)
            assertFails { offline.chapter(original, 1) }
        }
    }

    @Test
    fun invalidSurahResponseIsRejectedAndNeverCached() = runTest {
        val store = MemorySettingsStore()
        val client = FakeTranslationHttpClient { TranslationTestFixtures.surahJson(count = 6) }
        val repository = QuranEncTranslationRepository(client, store)

        assertFails { repository.verses(TranslationTestFixtures.edition(), 1) }
        assertNull(store.get("translation.cache.v1.english_saheeh.1.1.2.1"))
        assertNull(store.get("translation.chapter.v2.english_saheeh.1"))
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
