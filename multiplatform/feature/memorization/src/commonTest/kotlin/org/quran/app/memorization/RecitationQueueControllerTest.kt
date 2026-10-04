package org.quran.app.memorization

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.quran.app.domain.RecitationRepository
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
        controller.select("husary", listOf(second))
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
        controller.close()
        controller.close()
        late.complete("file:///disposed.mp3")
        runCurrent()
        assertTrue(ready.isEmpty())
        assertFalse(controller.state.value.isDownloading)
        assertEquals(3, clears)
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
            cache[reciterId to verse]?.let { return it }
            networkRequests++
            return fetch(reciterId, verse).also { cache[reciterId to verse] = it }
        }
    }
}
