package org.quran.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.quran.app.domain.RecitationRepository
import org.quran.app.model.Reciter
import org.quran.app.model.VerseId

class EveryAyahRecitationRepository(
    private val http: RecitationHttpClient,
    private val cache: RecitationFileCache,
) : RecitationRepository {
    private val downloadMutex = Mutex()
    override fun reciters(): List<Reciter> = EveryAyahCatalog.reciters
    override fun cached(reciterId: String, verse: VerseId): String? {
        val key = EveryAyahEndpoints.cacheKey(reciterId, verse)
        return cache.localUri(key)
    }
    override suspend fun download(reciterId: String, verse: VerseId): String = recitationStorageWork { downloadMutex.withLock {
        currentCoroutineContext().ensureActive()
        val key = EveryAyahEndpoints.cacheKey(reciterId, verse)
        cache.read(key)?.let { bytes ->
            if (Mp3Validation.isValid(bytes)) cache.localUri(key)?.let { return@withLock it }
            cache.remove(key)
        }
        val response = http.get(EveryAyahEndpoints.verse(reciterId, verse))
        currentCoroutineContext().ensureActive()
        require(response.status == 200) { "Recitation download was unsuccessful" }
        requireAudioContentType(response.contentType)
        require(Mp3Validation.isValid(response.bytes)) { "Recitation download is not supported MPEG Layer III frames" }
        cache.writeAtomic(key, response.bytes)
    } }
    override fun remove(reciterId: String, verse: VerseId) = cache.remove(EveryAyahEndpoints.cacheKey(reciterId, verse))
}
