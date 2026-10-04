package org.quran.app.data

internal const val MAX_RECITATION_BYTES = 20 * 1024 * 1024

data class RecitationResponse(val status: Int, val contentType: String?, val bytes: ByteArray)
fun interface RecitationHttpClient {
    suspend fun get(url: String): RecitationResponse
}

internal fun requireAudioContentType(type: String?) {
    require(type?.substringBefore(';')?.trim()?.lowercase() in setOf("audio/mpeg", "audio/mp3", "audio/x-mpeg", "application/octet-stream")) {
        "Recitation response is not an MP3 content type"
    }
}
