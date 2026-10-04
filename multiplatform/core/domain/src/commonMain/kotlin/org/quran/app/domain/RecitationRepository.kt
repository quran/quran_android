package org.quran.app.domain

import org.quran.app.model.Reciter
import org.quran.app.model.VerseId

interface RecitationRepository {
    fun reciters(): List<Reciter>
    fun cached(reciterId: String, verse: VerseId): String?
    suspend fun download(reciterId: String, verse: VerseId): String
    fun remove(reciterId: String, verse: VerseId)
}
