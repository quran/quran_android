package org.quran.app.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

class KtorTranslationHttpClient(
    private val client: HttpClient,
) : TranslationHttpClient {
    override suspend fun get(url: String): String = client.get(url).bodyAsText()
}
