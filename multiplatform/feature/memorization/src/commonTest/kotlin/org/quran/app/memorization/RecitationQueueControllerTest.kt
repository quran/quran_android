package org.quran.app.memorization

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.quran.app.domain.RecitationRepository
import org.quran.app.domain.RecitationLease
import org.quran.app.domain.RecitationStorageRepository
import org.quran.app.model.RecitationRemovalResult
import org.quran.app.model.RecitationStorageSnapshot
import org.quran.app.model.Reciter
import org.quran.app.model.VerseId

@OptIn(ExperimentalCoroutinesApi::class)
class RecitationQueueControllerTest {
    private val first = VerseId(1, 1)
    private val second = VerseId(1, 2)
    private val verses = listOf(first, second)

    @Test fun selectingIsExplicitAndPublishesOnlyTheCompleteSequentialQueue() = runTest {
        val repository = QueueRepository()
        val secondReady = CompletableDeferred<String>()
        repository.fetch = { _, verse -> if (verse == second) secondReady.await() else uri("alafasy", verse) }
        val ready = mutableListOf<Map<VerseId, String>>()
        var clears = 0
        val controller = RecitationQueueController(repository, this, ready::add, { clears++ })
        controller.select("alafasy", verses)
        assertTrue(repository.requests.isEmpty())
        assertEquals(1, clears)
        controller.prepareAudio()
        controller.prepareAudio()
        runCurrent()
        assertEquals(listOf("alafasy" to first, "alafasy" to second), repository.requests)
        assertTrue(ready.isEmpty())
        assertEquals(1, controller.state.value.completedDownloads)
        assertTrue(controller.state.value.isDownloading)
        secondReady.complete(uri("alafasy", second))
        runCurrent()
        assertEquals(listOf(mapOf(first to uri("alafasy", first), second to uri("alafasy", second))), ready)
        assertEquals(2, controller.state.value.completedDownloads)
        assertEquals(2, controller.state.value.cachedCount)
        assertFalse(controller.state.value.isDownloading)
    }

    @Test fun allCachedEntriesAreRevalidatedAndReusedOffline() = runTest {
        val repository = QueueRepository()
        verses.forEach { repository.cache["husary" to it] = uri("husary", it) }
        val ready = mutableListOf<Map<VerseId, String>>()
        val controller = RecitationQueueController(repository, this, ready::add)
        controller.select("husary", verses)
        assertEquals(2, controller.state.value.cachedCount)
        assertTrue(ready.isEmpty())
        controller.prepareAudio()
        runCurrent()
        assertEquals(0, repository.networkRequests)
        assertEquals(verses.toSet(), ready.single().keys)
        assertEquals(2, repository.requests.size)
    }

    @Test fun sourceChangeAndCloseSuppressLateNonCooperativeDownloads() = runTest {
        val repository = QueueRepository()
        val response = CompletableDeferred<String>()
        repository.fetch = { _, _ -> withContext(NonCancellable) { response.await() } }
        val ready = mutableListOf<Map<VerseId, String>>()
        var clears = 0
        val controller = RecitationQueueController(repository, this, ready::add, { clears++ })
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        assertEquals(2, clears) // Selection clears once; preparing clears again before replacing the source.
        controller.select("husary", listOf(second))
        assertEquals(3, clears)
        response.complete("file:///stale.mp3")
        runCurrent()
        assertTrue(ready.isEmpty())
        assertEquals("husary", controller.state.value.reciterId)
        assertEquals(0, controller.state.value.completedDownloads)
        assertFalse(controller.state.value.isDownloading)
        val late = CompletableDeferred<String>()
        repository.fetch = { _, _ -> withContext(NonCancellable) { late.await() } }
        controller.prepareAudio()
        runCurrent()
        assertEquals(4, clears)
        controller.close()
        controller.close()
        late.complete("file:///disposed.mp3")
        runCurrent()
        assertTrue(ready.isEmpty())
        assertFalse(controller.state.value.isDownloading)
        assertEquals(5, clears)
        assertEquals(2, repository.requests.size)
    }

    @Test fun partialFailureNeverPublishesAndRetryReusesPreviouslyCompletedEntries() = runTest {
        val repository = QueueRepository()
        var failSecond = true
        repository.fetch = { id, verse -> if (verse == second && failSecond) error("offline") else uri(id, verse) }
        val ready = mutableListOf<Map<VerseId, String>>()
        val controller = RecitationQueueController(repository, this, ready::add)
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        assertTrue(controller.state.value.failed)
        assertFalse(controller.state.value.isDownloading)
        assertEquals(1, controller.state.value.completedDownloads)
        assertTrue(ready.isEmpty())
        failSecond = false
        controller.prepareAudio()
        runCurrent()
        assertFalse(controller.state.value.failed)
        assertEquals(2, controller.state.value.completedDownloads)
        assertEquals(1, ready.size)
        assertEquals(3, repository.networkRequests)
    }

    @Test fun cancelledParentScopeLeavesNoReadyMapAndStopsProgress() = runTest {
        val child = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + child)
        val repository = QueueRepository()
        repository.fetch = { _, _ -> awaitCancellation() }
        val ready = mutableListOf<Map<VerseId, String>>()
        val controller = RecitationQueueController(repository, scope, ready::add)
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        child.cancel()
        runCurrent()
        assertFalse(controller.state.value.isDownloading)
        assertFalse(controller.state.value.failed)
        assertTrue(ready.isEmpty())
        controller.close()
    }

    @Test fun readyCallbackFailureIsRecoverableWithoutRedownloadingValidEntries() = runTest {
        val repository = QueueRepository()
        var attempts = 0
        val controller = RecitationQueueController(repository, this, { attempts++; error("audio preparation rejected") })
        controller.select("alafasy", listOf(first))
        controller.prepareAudio()
        runCurrent()
        assertTrue(controller.state.value.failed)
        assertFalse(controller.state.value.isDownloading)
        controller.prepareAudio()
        runCurrent()
        assertEquals(2, attempts)
        assertEquals(1, repository.networkRequests)
    }

    @Test fun selectionCopiesTheInputAndBoundsTheQueue() = runTest {
        val controller = RecitationQueueController(QueueRepository(), this, {})
        val mutable = verses.toMutableList()
        controller.select("alafasy", mutable)
        mutable.clear()
        assertEquals(verses, controller.state.value.verses)
        assertFailsWith<IllegalArgumentException> { controller.select("alafasy", emptyList()) }
        assertFailsWith<IllegalArgumentException> { controller.select("alafasy", listOf(first, first)) }
        assertFailsWith<IllegalArgumentException> { controller.select("alafasy", (1..21).map { VerseId(2, it) }) }
    }

    @Test fun successfulPreparationHoldsQueueLeaseUntilCloseAfterAudioCleared() = runTest {
        val events = mutableListOf<String>()
        val storage = QueueStorage(events)
        val ready = mutableListOf<Map<VerseId, String>>()
        val controller = RecitationQueueController(QueueRepository(), this, ready::add, { events += "cleared" }, storage)
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        assertEquals(1, storage.protectCalls)
        assertEquals(0, storage.releaseCalls)
        assertEquals(1, ready.size)
        assertTrue(events.indexOf("protect") < events.indexOfFirst { it.startsWith("download") })
        controller.close()
        runCurrent()
        assertEquals(1, storage.releaseCalls)
        assertTrue(events.indexOfLast { it == "cleared" } < events.indexOfLast { it == "release" })
    }

    @Test fun selectionReleasesPublishedLeaseOnlyAfterClearingOldAudio() = runTest {
        val events = mutableListOf<String>()
        val storage = QueueStorage(events)
        val controller = RecitationQueueController(QueueRepository(), this, {}, { events += "cleared" }, storage)
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        events.clear()
        controller.select("husary", listOf(second))
        runCurrent()
        assertEquals(listOf("cleared", "release"), events)
        assertEquals(1, storage.releaseCalls)
    }

    @Test fun failedAttemptReleasesLeaseBeforeRetryAndSuccessfulRetryKeepsItsLease() = runTest {
        val events = mutableListOf<String>()
        val storage = QueueStorage(events)
        val repository = QueueRepository()
        var fail = true
        repository.fetch = { id, verse -> if (fail && verse == second) error("offline") else uri(id, verse) }
        val controller = RecitationQueueController(repository, this, {}, storage = storage)
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        assertEquals(1, storage.releaseCalls)
        fail = false
        controller.prepareAudio()
        runCurrent()
        assertEquals(2, storage.protectCalls)
        assertEquals(1, storage.releaseCalls)
        controller.close()
        runCurrent()
        assertEquals(2, storage.releaseCalls)
    }

    @Test fun cancelledPreparationReleasesLeaseEvenWhenParentScopeIsCancelled() = runTest {
        val events = mutableListOf<String>()
        val storage = QueueStorage(events)
        val child = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + child)
        val repository = QueueRepository().apply { fetch = { _, _ -> awaitCancellation() } }
        val controller = RecitationQueueController(repository, scope, {}, storage = storage)
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        assertEquals(0, storage.releaseCalls)
        child.cancel()
        runCurrent()
        assertEquals(1, storage.releaseCalls)
        assertFalse(controller.state.value.isDownloading)
        controller.close()
    }

    @Test fun cancellationWhileProtectingStillReleasesLateAcquiredLease() = runTest {
        val events = mutableListOf<String>()
        val storage = QueueStorage(events)
        val acquisition = CompletableDeferred<RecitationLease>()
        storage.acquire = { withContext(NonCancellable) { acquisition.await() } }
        val controller = RecitationQueueController(QueueRepository(), this, {}, storage = storage)
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        controller.select("husary", listOf(second))
        acquisition.complete(storage.lease())
        runCurrent()
        assertEquals(1, storage.releaseCalls)
        assertTrue(storage.events.none { it.startsWith("download") })
    }

    @Test fun closingAfterParentScopeCancellationStillReleasesPublishedLease() = runTest {
        val events = mutableListOf<String>()
        val storage = QueueStorage(events)
        val child = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + child)
        val controller = RecitationQueueController(QueueRepository(), scope, {}, storage = storage)
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        assertEquals(0, storage.releaseCalls)
        child.cancel()
        runCurrent()
        controller.close()
        runCurrent()
        assertEquals(1, storage.releaseCalls)
    }

    @Test fun replacingNativeSourceReleasesQueueLeaseAfterCallerClearsOldSource() = runTest {
        val events = mutableListOf<String>()
        val storage = QueueStorage(events)
        val controller = RecitationQueueController(QueueRepository(), this, {}, { events += "audio-cleared" }, storage)
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        events.clear()
        events += "native-source-cleared"
        controller.releasePublishedAudioAfterSourceReplacementAndWait()
        runCurrent()
        assertEquals(listOf("native-source-cleared", "release"), events)
        assertEquals(1, storage.releaseCalls)
    }

    @Test fun failedAudioClearKeepsLeaseUntilClearCanBeRetried() = runTest {
        val events = mutableListOf<String>()
        val storage = QueueStorage(events)
        var failClear = false
        val controller = RecitationQueueController(
            QueueRepository(), this, {}, {
                events += "clear"
                if (failClear) { failClear = false; error("native source still attached") }
            }, storage,
        )
        controller.select("alafasy", verses)
        controller.prepareAudio()
        runCurrent()
        failClear = true
        assertFailsWith<IllegalStateException> { controller.select("husary", listOf(second)) }
        assertEquals(0, storage.releaseCalls)
        controller.select("husary", listOf(second))
        runCurrent()
        assertEquals(1, storage.releaseCalls)
        assertTrue(events.indexOfLast { it == "clear" } < events.indexOfLast { it == "release" })
    }

    private fun uri(id: String, verse: VerseId) = "file:///$id-${verse.surah}-${verse.ayah}.mp3"
    private inner class QueueRepository : RecitationRepository {
        val cache = mutableMapOf<Pair<String, VerseId>, String>()
        val requests = mutableListOf<Pair<String, VerseId>>()
        var networkRequests = 0
        var fetch: suspend (String, VerseId) -> String = { id, verse -> uri(id, verse) }
        override fun reciters(): List<Reciter> = emptyList()
        override fun cached(reciterId: String, verse: VerseId) = cache[reciterId to verse]
        override fun remove(reciterId: String, verse: VerseId) { cache.remove(reciterId to verse) }
        override suspend fun download(reciterId: String, verse: VerseId): String {
            requests += reciterId to verse
            storageEvents?.add("download:${verse.ayah}")
            cache[reciterId to verse]?.let { return it }
            networkRequests++
            return fetch(reciterId, verse).also { cache[reciterId to verse] = it }
        }
    }

    private var storageEvents: MutableList<String>? = null

    private inner class QueueStorage(val events: MutableList<String>) : RecitationStorageRepository {
        var protectCalls = 0
        var releaseCalls = 0
        var acquire: suspend () -> RecitationLease = { lease() }
        init { storageEvents = events }
        override suspend fun inventory() = RecitationStorageSnapshot(emptyList(), 0, 0)
        override suspend fun removeDownload(reciterId: String, verseId: VerseId) = RecitationRemovalResult.NOT_FOUND
        override suspend fun protect(reciterId: String, verses: List<VerseId>): RecitationLease {
            protectCalls++
            events += "protect"
            return acquire()
        }
        fun lease() = object : RecitationLease {
            private var released = false
            override suspend fun release() {
                if (!released) {
                    released = true
                    releaseCalls++
                    events += "release"
                }
            }
        }
    }
}
