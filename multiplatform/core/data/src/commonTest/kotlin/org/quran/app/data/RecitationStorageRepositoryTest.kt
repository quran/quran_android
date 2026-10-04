package org.quran.app.data

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.quran.app.domain.RecitationCacheLimitExceededException
import org.quran.app.model.RecitationRemovalResult
import org.quran.app.model.VerseId

class RecitationStorageRepositoryTest {
    private val first = VerseId(1, 1)
    private val second = VerseId(1, 2)

    @Test fun inventoryTotalsCanonicalFilesAndIgnoresTemporaryForeignOrInvalidAddresses() = runTest {
        val cache = FakeRecitationCache()
        cache.bytes[EveryAyahEndpoints.cacheKey("alafasy", first)] = syntheticMp3()
        cache.bytes[EveryAyahEndpoints.cacheKey("husary", second)] = ByteArray(123)
        cache.bytes["recitation-123.part"] = ByteArray(999)
        cache.bytes["recording.wav"] = ByteArray(400)
        cache.bytes["alafasy-114999.mp3"] = ByteArray(100)
        cache.bytes["unknown-001001.mp3"] = ByteArray(100)
        val repository = EveryAyahRecitationRepository(RecitationHttpClient { validResponse() }, cache)
        val inventory = repository.inventory()
        assertEquals(2, inventory.entries.size)
        assertEquals(957L, inventory.totalBytes)
        assertEquals(256L * 1024 * 1024, inventory.limitBytes)
        assertEquals(listOf("alafasy", "husary"), inventory.entries.map { it.reciterId })
        assertEquals(listOf(first, second), inventory.entries.map { it.verseId })
        assertEquals(RecitationRemovalResult.REMOVED, repository.removeDownload("husary", second))
        assertEquals(834L, repository.inventory().totalBytes)
        assertEquals(RecitationRemovalResult.NOT_FOUND, repository.removeDownload("husary", second))
        assertFailsWith<IllegalArgumentException> { repository.removeDownload("../escape", first) }
    }

    @Test fun overlappingLeasesProtectEntireQueueAndReleaseIsIdempotent() = runTest {
        val cache = FakeRecitationCache()
        val repository = EveryAyahRecitationRepository(RecitationHttpClient { validResponse() }, cache)
        val rangeLease = repository.protect("alafasy", listOf(first, second))
        val otherLease = repository.protect("alafasy", listOf(first))
        repository.download("alafasy", first)
        repository.download("alafasy", second)
        assertEquals(RecitationRemovalResult.IN_USE, repository.removeDownload("alafasy", first))
        assertEquals(RecitationRemovalResult.IN_USE, repository.removeDownload("alafasy", second))
        assertFailsWith<IllegalStateException> { repository.remove("alafasy", first) }
        rangeLease.release()
        rangeLease.release()
        assertEquals(RecitationRemovalResult.REMOVED, repository.removeDownload("alafasy", second))
        assertEquals(RecitationRemovalResult.IN_USE, repository.removeDownload("alafasy", first))
        otherLease.release()
        assertEquals(RecitationRemovalResult.REMOVED, repository.removeDownload("alafasy", first))
    }

    @Test fun capAllowsExactBoundaryThenRejectsPublicationWithoutEvictingExistingAudio() = runTest {
        val cache = FakeRecitationCache()
        val repository = EveryAyahRecitationRepository(RecitationHttpClient { validResponse() }, cache, maxCacheBytes = 834)
        val uri = repository.download("alafasy", first)
        assertEquals(834L, repository.inventory().totalBytes)
        assertFailsWith<RecitationCacheLimitExceededException> { repository.download("alafasy", second) }
        assertEquals(uri, repository.download("alafasy", first))
        assertNull(repository.cached("alafasy", second))
        assertEquals(1, cache.writes)
        assertEquals(RecitationRemovalResult.REMOVED, repository.removeDownload("alafasy", first))
        assertNotNull(repository.download("alafasy", second))
    }

    @Test fun legacyCacheAboveCapRemainsAvailableUntilLearnerRemovesFiles() = runTest {
        val cache = FakeRecitationCache()
        cache.bytes[EveryAyahEndpoints.cacheKey("alafasy", first)] = syntheticMp3()
        cache.bytes[EveryAyahEndpoints.cacheKey("alafasy", second)] = syntheticMp3()
        var requests = 0
        val repository = EveryAyahRecitationRepository(RecitationHttpClient { requests++; validResponse() }, cache, maxCacheBytes = 834)
        assertEquals(1668L, repository.inventory().totalBytes)
        assertNotNull(repository.download("alafasy", first))
        assertEquals(0, requests)
        assertFailsWith<RecitationCacheLimitExceededException> { repository.download("husary", first) }
        assertEquals(0, requests, "A cache already at capacity should reject new network requests")
        assertEquals(2, repository.inventory().entries.size)
    }

    @Test fun failedRepairPreservesProtectedOldSourceAndSuccessfulReplacementUsesNetCapacity() = runTest {
        val cache = FakeRecitationCache()
        val key = EveryAyahEndpoints.cacheKey("alafasy", first)
        val corrupt = ByteArray(834)
        cache.bytes[key] = corrupt
        var failing = true
        val repository = EveryAyahRecitationRepository(RecitationHttpClient {
            if (failing) error("offline") else validResponse()
        }, cache, maxCacheBytes = 834)
        val lease = repository.protect("alafasy", listOf(first))
        assertFailsWith<IllegalStateException> { repository.download("alafasy", first) }
        assertContentEquals(corrupt, cache.bytes[key])
        assertEquals(RecitationRemovalResult.IN_USE, repository.removeDownload("alafasy", first))
        failing = false
        assertNotNull(repository.download("alafasy", first))
        assertContentEquals(syntheticMp3(), cache.bytes[key])
        assertEquals(834L, repository.inventory().totalBytes)
        lease.release()
    }

    @Test fun canceledDownloadsReleaseMutexAndNeverPublishIntoInventory() = runTest {
        val entered = CompletableDeferred<Unit>()
        val cache = FakeRecitationCache()
        val repository = EveryAyahRecitationRepository(RecitationHttpClient { entered.complete(Unit); awaitCancellation() }, cache)
        val download = launch { repository.download("alafasy", first) }
        entered.await()
        download.cancelAndJoin()
        assertTrue(repository.inventory().entries.isEmpty())
        val lease = repository.protect("alafasy", listOf(first))
        assertEquals(RecitationRemovalResult.IN_USE, repository.removeDownload("alafasy", first))
        lease.release()
        assertEquals(RecitationRemovalResult.NOT_FOUND, repository.removeDownload("alafasy", first))
    }

    @Test fun leaseReleaseWorksFromACanceledCoroutine() = runTest {
        val repository = EveryAyahRecitationRepository(RecitationHttpClient { validResponse() }, FakeRecitationCache())
        repository.download("alafasy", first)
        val retained = CompletableDeferred<Unit>()
        val job = launch {
            val lease = repository.protect("alafasy", listOf(first))
            retained.complete(Unit)
            try { awaitCancellation() } finally { lease.release() }
        }
        retained.await()
        assertEquals(RecitationRemovalResult.IN_USE, repository.removeDownload("alafasy", first))
        job.cancelAndJoin()
        assertEquals(RecitationRemovalResult.REMOVED, repository.removeDownload("alafasy", first))
    }
}
