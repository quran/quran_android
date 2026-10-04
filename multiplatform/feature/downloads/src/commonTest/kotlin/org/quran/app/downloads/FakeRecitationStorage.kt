package org.quran.app.downloads

import kotlinx.coroutines.CompletableDeferred
import org.quran.app.domain.RecitationLease
import org.quran.app.domain.RecitationStorageRepository
import org.quran.app.model.*

internal class FakeRecitationStorage(var entries: List<RecitationDownload>) : RecitationStorageRepository {
    var result = RecitationRemovalResult.REMOVED
    var removals = 0
    var inventoryAction: (suspend () -> RecitationStorageSnapshot)? = null
    var removalGate: CompletableDeferred<Unit>? = null
    fun snapshot() = RecitationStorageSnapshot(entries.toList(), entries.sumOf { it.sizeBytes }, 256L * 1024 * 1024)
    override suspend fun inventory() = inventoryAction?.invoke() ?: snapshot()
    override suspend fun removeDownload(reciterId: String, verseId: VerseId): RecitationRemovalResult {
        removals++
        removalGate?.await()
        if (result == RecitationRemovalResult.REMOVED) entries = entries.filterNot { it.reciterId == reciterId && it.verseId == verseId }
        return result
    }
    override suspend fun protect(reciterId: String, verses: List<VerseId>) = object : RecitationLease {
        override suspend fun release() = Unit
    }
}
