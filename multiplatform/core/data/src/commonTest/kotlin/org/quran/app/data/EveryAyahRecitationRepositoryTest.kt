package org.quran.app.data

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.quran.app.model.VerseId

@OptIn(ExperimentalCoroutinesApi::class)
class EveryAyahRecitationRepositoryTest {
    private val verse = VerseId(18, 75)

    @Test fun canonicalEndpointsOnlyUseTheThreeTrustedFolders() {
        assertEquals(listOf("alafasy", "husary", "sudais"), EveryAyahCatalog.reciters.map { it.id })
        assertEquals("https://everyayah.com/data/Alafasy_128kbps/018075.mp3", EveryAyahEndpoints.verse("alafasy", verse))
        assertEquals("https://everyayah.com/data/Husary_128kbps/114006.mp3", EveryAyahEndpoints.verse("husary", VerseId(114, 6)))
        assertEquals("https://everyayah.com/data/Abdurrahmaan_As-Sudais_192kbps/001001.mp3", EveryAyahEndpoints.verse("sudais", VerseId(1, 1)))
        listOf("../alafasy", "https://evil.example", "Alafasy_128kbps", "warsh").forEach { id ->
            assertFailsWith<IllegalArgumentException> { EveryAyahEndpoints.verse(id, verse) }
        }
    }

    @Test fun offlineDownloadReusesTheCompletedLocalFileWithoutNetwork() = runTest {
        val cache = FakeRecitationCache()
        var requests = 0
        val repository = EveryAyahRecitationRepository(RecitationHttpClient { requests++; validResponse() }, cache)
        val uri = repository.download("alafasy", verse)
        assertTrue(uri.startsWith("file://"))
        assertEquals(uri, repository.cached("alafasy", verse))
        assertEquals(uri, repository.download("alafasy", verse))
        assertEquals(1, requests)
        repository.remove("alafasy", verse)
        assertNull(repository.cached("alafasy", verse))
    }

    @Test fun invalidStatusTypeSignatureAndOversizeNeverEnterCache() = runTest {
        val invalid = listOf(
            RecitationResponse(404, "audio/mpeg", syntheticMp3()),
            RecitationResponse(200, "text/html", syntheticMp3()),
            RecitationResponse(200, "audio/mpeg", "<html>error</html>".encodeToByteArray()),
            RecitationResponse(200, "audio/mpeg", byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0)),
            RecitationResponse(200, "audio/mpeg", ByteArray(MAX_RECITATION_BYTES + 1)),
        )
        invalid.forEach { response ->
            val cache = FakeRecitationCache()
            val repository = EveryAyahRecitationRepository(RecitationHttpClient { response }, cache)
            assertFailsWith<IllegalArgumentException> { repository.download("alafasy", verse) }
            assertNull(repository.cached("alafasy", verse))
            assertEquals(0, cache.writes)
        }
    }

    @Test fun cancellationDuringNetworkLeavesNoFileAndDoesNotBecomeAnErrorResult() = runTest {
        val cache = FakeRecitationCache()
        val entered = CompletableDeferred<Unit>()
        val repository = EveryAyahRecitationRepository(RecitationHttpClient {
            entered.complete(Unit); awaitCancellation()
        }, cache)
        val job = launch { repository.download("alafasy", verse) }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertNull(repository.cached("alafasy", verse))
        assertEquals(0, cache.writes)
    }

    @Test fun damagedExistingFileIsRemovedBeforeRetryingDownload() = runTest {
        val cache = FakeRecitationCache()
        cache.bytes[EveryAyahEndpoints.cacheKey("alafasy", verse)] = "broken".encodeToByteArray()
        val repository = EveryAyahRecitationRepository(RecitationHttpClient { validResponse() }, cache)
        // cached() reports metadata only; download() validates before native playback.
        assertNotNull(repository.cached("alafasy", verse))
        assertNotNull(repository.download("alafasy", verse))
        assertContentEquals(syntheticMp3(), cache.bytes[EveryAyahEndpoints.cacheKey("alafasy", verse)])
    }

    @Test fun truncatedLaterFramesAndTrailingGarbageAreRejectedButId3v1IsAccepted() {
        assertFalse(Mp3Validation.isValid(syntheticMp3() + syntheticMp3().copyOf(100)))
        assertFalse(Mp3Validation.isValid(syntheticMp3() + byteArrayOf(1, 2, 3)))
        val trailingTag = ByteArray(128).apply { this[0] = 84; this[1] = 65; this[2] = 71 }
        assertTrue(Mp3Validation.isValid(syntheticMp3() + trailingTag))
    }

    @Test fun id3TaggedMp3IsAcceptedButReservedOrTruncatedHeadersAreRejected() {
        val tag = byteArrayOf(73, 68, 51, 4, 0, 0, 0, 0, 0, 3, 1, 2, 3)
        assertTrue(Mp3Validation.isValid(tag + syntheticMp3()))
        assertFalse(Mp3Validation.isValid(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0)))
        assertFalse(Mp3Validation.isValid(byteArrayOf(73, 68, 51, 4, 0, 0, 127, 127, 127, 127)))
    }
}

internal fun syntheticMp3(): ByteArray = ByteArray(834).apply {
    // Two complete MPEG1 Layer III frames, 128 kbps / 44.1 kHz. No Quran audio.
    listOf(0, 417).forEach { offset -> this[offset] = 0xff.toByte(); this[offset + 1] = 0xfb.toByte(); this[offset + 2] = 0x90.toByte() }
}
internal fun validResponse() = RecitationResponse(200, "audio/mpeg", syntheticMp3())
internal class FakeRecitationCache : RecitationFileCache {
    val bytes = mutableMapOf<String, ByteArray>()
    var writes = 0
    override fun read(key: String) = bytes[key]
    override fun localUri(key: String): String? = bytes[key]?.let { "file:///test/$key" }
    override suspend fun writeAtomic(key: String, content: ByteArray): String {
        currentCoroutineContext().ensureActive(); writes++; bytes[key] = content.copyOf(); return "file:///test/$key"
    }
    override fun remove(key: String) { bytes.remove(key) }
}
