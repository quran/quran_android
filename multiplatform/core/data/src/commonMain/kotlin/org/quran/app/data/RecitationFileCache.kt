package org.quran.app.data

/** App-private durable files; writeAtomic must never expose a partial final file. */
interface RecitationFileCache {
    fun read(key: String): ByteArray?
    fun localUri(key: String): String?
    suspend fun writeAtomic(key: String, content: ByteArray): String
    fun remove(key: String)
}
internal fun requireRecitationCacheKey(key: String) {
    require(key.matches(Regex("(?:alafasy|husary|sudais)-[0-9]{6}\\.mp3"))) { "Invalid recitation cache key" }
}
internal expect fun platformRecitationFileCache(): RecitationFileCache

internal expect suspend fun <T> recitationStorageWork(block: suspend () -> T): T
