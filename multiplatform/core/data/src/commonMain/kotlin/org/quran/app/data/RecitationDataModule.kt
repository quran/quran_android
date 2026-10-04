package org.quran.app.data

import io.ktor.client.HttpClient
import org.quran.app.domain.RecitationRepository
import org.quran.app.domain.RecitationStorageRepository

/** Owns the network engine; close when the app composition is disposed. */
class RecitationDataModule(
    private val client: HttpClient = platformRecitationHttpClient(),
    cache: RecitationFileCache = platformRecitationFileCache(),
    maxCacheBytes: Long = DEFAULT_RECITATION_CACHE_LIMIT_BYTES,
) {
    private val implementation = EveryAyahRecitationRepository(KtorRecitationHttpClient(client), cache, maxCacheBytes)
    val repository: RecitationRepository = implementation
    val storage: RecitationStorageRepository = implementation
    fun close() = client.close()
}
internal expect fun platformRecitationHttpClient(): HttpClient
