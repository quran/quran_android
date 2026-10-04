package org.quran.app.data

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Reads in bounded chunks; rejects errors and oversize bodies before final allocation. */
class KtorRecitationHttpClient(private val client: HttpClient) : RecitationHttpClient {
    override suspend fun get(url: String): RecitationResponse {
        EveryAyahEndpoints.requireTrustedUrl(url)
        return client.prepareGet(url).execute { response ->
            require(response.status.value == 200) { "Recitation download was unsuccessful" }
            val type = response.headers[HttpHeaders.ContentType]
            requireAudioContentType(type)
            val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            require(declaredLength == null || declaredLength in 1..MAX_RECITATION_BYTES.toLong()) { "Recitation exceeds download limit" }
            val chunks = mutableListOf<ByteArray>()
            val buffer = ByteArray(8192)
            var total = 0
            val channel = response.bodyAsChannel()
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = channel.readAvailable(buffer, 0, buffer.size)
                if (count < 0) break
                if (count == 0) continue
                require(count <= MAX_RECITATION_BYTES - total) { "Recitation exceeds download limit" }
                chunks += buffer.copyOf(count)
                total += count
            }
            require(declaredLength == null || declaredLength == total.toLong()) { "Incomplete recitation response" }
            val bytes = ByteArray(total)
            var offset = 0
            chunks.forEach { chunk -> chunk.copyInto(bytes, offset); offset += chunk.size }
            RecitationResponse(response.status.value, type, bytes)
        }
    }
}
