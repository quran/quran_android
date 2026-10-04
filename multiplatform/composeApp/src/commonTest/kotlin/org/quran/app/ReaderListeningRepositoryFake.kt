package org.quran.app

import org.quran.app.domain.RecitationRepository
import org.quran.app.model.Reciter
import org.quran.app.model.VerseId

internal class ReaderListeningRepositoryFake : RecitationRepository {
    val requests = mutableListOf<Pair<String, VerseId>>()
    var fetch: suspend (String, VerseId) -> String = { id, verse -> "file:///$id-${verse.surah}-${verse.ayah}.mp3" }
    override fun reciters(): List<Reciter> = emptyList()
    override fun cached(reciterId: String, verse: VerseId): String? = null
    override suspend fun download(reciterId: String, verse: VerseId): String {
        requests += reciterId to verse
        return fetch(reciterId, verse)
    }
    override fun remove(reciterId: String, verse: VerseId) = Unit
}
