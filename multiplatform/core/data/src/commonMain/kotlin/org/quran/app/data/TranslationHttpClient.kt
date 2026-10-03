package org.quran.app.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/** Small transport port keeps data tests independent from real networking. */
interface TranslationHttpClient {
    suspend fun get(url: String): String
}

class KtorTranslationHttpClient(
    private val client: HttpClient,
) : TranslationHttpClient {
    override suspend fun get(url: String): String = client.get(url).bodyAsText()
}
