package org.quran.app.domain

import org.quran.app.model.RecitationRemovalResult
import org.quran.app.model.RecitationStorageSnapshot
import org.quran.app.model.VerseId

interface RecitationStorageRepository {
    suspend fun inventory(): RecitationStorageSnapshot
    suspend fun removeDownload(reciterId: String, verseId: VerseId): RecitationRemovalResult
    /** May protect not-yet-downloaded verses; acquisition must precede queue preparation. */
    suspend fun protect(reciterId: String, verses: List<VerseId>): RecitationLease
}
