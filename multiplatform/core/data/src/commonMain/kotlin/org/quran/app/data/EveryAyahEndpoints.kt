package org.quran.app.data

import org.quran.app.model.VerseId

object EveryAyahEndpoints {
    private const val BASE_URL = "https://everyayah.com/data/"
    fun verse(reciterId: String, verse: VerseId): String =
        BASE_URL + EveryAyahCatalog.requireReciter(reciterId).folder + "/" + filename(verse)
    fun cacheKey(reciterId: String, verse: VerseId): String {
        EveryAyahCatalog.requireReciter(reciterId)
        return "$reciterId-${filename(verse)}"
    }
    private fun filename(verse: VerseId): String =
        verse.surah.toString().padStart(3, '0') + verse.ayah.toString().padStart(3, '0') + ".mp3"

    internal fun requireTrustedUrl(url: String) {
        require(EveryAyahCatalog.reciters.any { reciter ->
            url.startsWith(BASE_URL + reciter.folder + "/") &&
                url.removePrefix(BASE_URL + reciter.folder + "/").matches(Regex("[0-9]{6}\\.mp3"))
        }) { "Untrusted recitation endpoint" }
    }
}
