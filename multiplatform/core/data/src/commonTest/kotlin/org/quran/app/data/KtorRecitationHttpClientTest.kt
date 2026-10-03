package org.quran.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.quran.app.model.VerseId

class KtorRecitationHttpClientTest {
    private val url = EveryAyahEndpoints.verse("alafasy", VerseId(1, 1))

    @Test fun acceptedResponseIsReadCompletelyAndUntrustedHostNeverReachesEngine() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond(ByteReadChannel(syntheticMp3()), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "audio/mpeg"))
        })
        try {
            val transport = KtorRecitationHttpClient(client)
            assertContentEquals(syntheticMp3(), transport.get(url).bytes)
            assertFailsWith<IllegalArgumentException> { transport.get("https://evil.example/001001.mp3") }
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test fun declaredOversizeAndActualOversizeWithoutLengthAreBothBounded() = runTest {
        val cases = listOf(
            byteArrayOf(1) to headersOf(HttpHeaders.ContentType to listOf("audio/mpeg"), HttpHeaders.ContentLength to listOf((MAX_RECITATION_BYTES + 1).toString())),
            ByteArray(MAX_RECITATION_BYTES + 1) to headersOf(HttpHeaders.ContentType, "audio/mpeg"),
            syntheticMp3() to headersOf(HttpHeaders.ContentType to listOf("audio/mpeg"), HttpHeaders.ContentLength to listOf("900")),
        )
        cases.forEach { (body, headers) ->
            val client = HttpClient(MockEngine { respond(ByteReadChannel(body), HttpStatusCode.OK, headers) })
            try { assertFailsWith<IllegalArgumentException> { KtorRecitationHttpClient(client).get(url) } }
            finally { client.close() }
        }
    }

    @Test fun redirectsHttpErrorsAndHtmlAreRejected() = runTest {
        listOf(HttpStatusCode.Found to "audio/mpeg", HttpStatusCode.NotFound to "audio/mpeg", HttpStatusCode.OK to "text/html").forEach { (status, type) ->
            val client = HttpClient(MockEngine { respond("error", status, headersOf(HttpHeaders.ContentType, type)) }) { followRedirects = false }
            try { assertFailsWith<IllegalArgumentException> { KtorRecitationHttpClient(client).get(url) } }
            finally { client.close() }
        }
    }

    @Test fun inFlightHttpCancellationPropagates() = runTest {
        val entered = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { entered.complete(Unit); awaitCancellation() })
        try {
            val job = launch { KtorRecitationHttpClient(client).get(url) }
            entered.await()
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        } finally { client.close() }
    }
}
