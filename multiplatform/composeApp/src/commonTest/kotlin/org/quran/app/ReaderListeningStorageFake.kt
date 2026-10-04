package org.quran.app

import org.quran.app.domain.RecitationLease
import org.quran.app.domain.RecitationStorageRepository
import org.quran.app.model.*

internal class ReaderListeningStorageFake(private val events: MutableList<String>) : RecitationStorageRepository {
    var protected = 0
    val protectedVerses = mutableListOf<List<VerseId>>()
    override suspend fun inventory() = RecitationStorageSnapshot(emptyList(), 0, 256L * 1024 * 1024)
    override suspend fun removeDownload(reciterId: String, verseId: VerseId) = RecitationRemovalResult.NOT_FOUND
    override suspend fun protect(reciterId: String, verses: List<VerseId>): RecitationLease {
        protected++
        protectedVerses += verses.toList()
        events += "protect"
        return object : RecitationLease {
            private var released = false
            override suspend fun release() {
                if (!released) { released = true; protected--; events += "release" }
            }
        }
    }
}
