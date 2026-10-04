package org.quran.app.data

import io.ktor.client.HttpClient
import org.quran.app.domain.RecitationRepository

/** Owns the network engine; close when the app composition is disposed. */
class RecitationDataModule(
    private val client: HttpClient = platformRecitationHttpClient(),
    cache: RecitationFileCache = platformRecitationFileCache(),
) {
    val repository: RecitationRepository = EveryAyahRecitationRepository(KtorRecitationHttpClient(client), cache)
    fun close() = client.close()
}
internal expect fun platformRecitationHttpClient(): HttpClient
