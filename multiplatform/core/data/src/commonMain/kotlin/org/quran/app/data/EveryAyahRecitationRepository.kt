package org.quran.app.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.quran.app.domain.RecitationCacheLimitExceededException
import org.quran.app.domain.RecitationLease
import org.quran.app.domain.RecitationRepository
import org.quran.app.domain.RecitationStorageRepository
import org.quran.app.model.Reciter
import org.quran.app.model.RecitationRemovalResult
import org.quran.app.model.RecitationStorageSnapshot
import org.quran.app.model.VerseId

internal const val DEFAULT_RECITATION_CACHE_LIMIT_BYTES = 256L * 1024 * 1024

class EveryAyahRecitationRepository(
    private val http: RecitationHttpClient,
    private val cache: RecitationFileCache,
    private val maxCacheBytes: Long = DEFAULT_RECITATION_CACHE_LIMIT_BYTES,
) : RecitationRepository, RecitationStorageRepository {
    private val storageMutex = Mutex()
    private val protectedKeys = mutableMapOf<String, Int>()
    init { require(maxCacheBytes > 0) { "Storage limit must be positive" } }

    override fun reciters(): List<Reciter> = EveryAyahCatalog.reciters
    override fun cached(reciterId: String, verse: VerseId): String? = cache.localUri(EveryAyahEndpoints.cacheKey(reciterId, verse))

    override suspend fun download(reciterId: String, verse: VerseId): String = recitationStorageWork {
        storageMutex.withLock {
            currentCoroutineContext().ensureActive()
            val key = EveryAyahEndpoints.cacheKey(reciterId, verse)
            cache.read(key)?.let { bytes ->
                if (Mp3Validation.isValid(bytes)) cache.localUri(key)?.let { return@withLock it }
                // Keep the old inode until a complete validated replacement is atomically published.
                // A live player may still own it; failed/canceled repair must not delete its source.
            }
            if (snapshot(excludingKey = key).totalBytes >= maxCacheBytes) {
                throw RecitationCacheLimitExceededException(maxCacheBytes)
            }
            val response = http.get(EveryAyahEndpoints.verse(reciterId, verse))
            currentCoroutineContext().ensureActive()
            require(response.status == 200) { "Recitation download was unsuccessful" }
            requireAudioContentType(response.contentType)
            require(Mp3Validation.isValid(response.bytes)) { "Recitation download is not supported MPEG Layer III frames" }
            val total = snapshot(excludingKey = key).totalBytes
            if (total > maxCacheBytes || response.bytes.size.toLong() > maxCacheBytes - total) {
                throw RecitationCacheLimitExceededException(maxCacheBytes)
            }
            currentCoroutineContext().ensureActive()
            cache.writeAtomic(key, response.bytes)
        }
    }

    override suspend fun inventory(): RecitationStorageSnapshot = recitationStorageWork {
        storageMutex.withLock { snapshot() }
    }

    override suspend fun removeDownload(reciterId: String, verseId: VerseId): RecitationRemovalResult = recitationStorageWork {
        storageMutex.withLock {
            val key = EveryAyahEndpoints.cacheKey(reciterId, verseId)
            when {
                key in protectedKeys -> RecitationRemovalResult.IN_USE
                cache.entries().none { it.key == key } -> RecitationRemovalResult.NOT_FOUND
                else -> { cache.remove(key); RecitationRemovalResult.REMOVED }
            }
        }
    }

    override suspend fun protect(reciterId: String, verses: List<VerseId>): RecitationLease {
        currentCoroutineContext().ensureActive()
        require(verses.size in 1..20 && verses.distinct().size == verses.size) { "Protect between one and twenty distinct verses" }
        val keys = verses.map { EveryAyahEndpoints.cacheKey(reciterId, it) }
        // No dispatcher hop/suspension after registration: cancellation cannot discard a registered lease.
        return storageMutex.withLock {
            keys.forEach { key -> protectedKeys[key] = (protectedKeys[key] ?: 0) + 1 }
            object : RecitationLease {
                private var released = false
                override suspend fun release() = withContext(NonCancellable) {
                    storageMutex.withLock {
                        if (!released) {
                            released = true
                            keys.forEach { key ->
                                val count = checkNotNull(protectedKeys[key])
                                if (count == 1) protectedKeys.remove(key) else protectedKeys[key] = count - 1
                            }
                        }
                    }
                }
            }
        }
    }

    /** Legacy synchronous deletion also rejects protected/busy storage. UI uses removeDownload. */
    override fun remove(reciterId: String, verse: VerseId) {
        val key = EveryAyahEndpoints.cacheKey(reciterId, verse)
        check(storageMutex.tryLock()) { "Recitation storage is busy" }
        try {
            check(key !in protectedKeys) { "Recitation is in use" }
            cache.remove(key)
        } finally { storageMutex.unlock() }
    }

    private fun snapshot(excludingKey: String? = null): RecitationStorageSnapshot {
        val entries = cache.entries().filter { it.key != excludingKey }.mapNotNull(RecitationCacheMetadata::parse)
            .sortedWith(compareBy({ it.reciterId }, { it.verseId.surah }, { it.verseId.ayah }))
        val total = entries.fold(0L) { sum, entry -> if (entry.sizeBytes > Long.MAX_VALUE - sum) Long.MAX_VALUE else sum + entry.sizeBytes }
        return RecitationStorageSnapshot(entries, total, maxCacheBytes)
    }
}
