package org.quran.app.data

import org.quran.app.model.RecitationDownload
import org.quran.app.model.VerseId

internal object RecitationCacheMetadata {
    private val keyPattern = Regex("(alafasy|husary|sudais)-([0-9]{3})([0-9]{3})\\.mp3")
    fun parse(entry: RecitationCacheEntry): RecitationDownload? {
        if (entry.sizeBytes < 0) return null
        val match = keyPattern.matchEntire(entry.key) ?: return null
        val id = match.groupValues[1]
        val verse = runCatching { VerseId(match.groupValues[2].toInt(), match.groupValues[3].toInt()) }.getOrNull() ?: return null
        if (EveryAyahEndpoints.cacheKey(id, verse) != entry.key) return null
        return RecitationDownload(id, verse, entry.sizeBytes)
    }
}
