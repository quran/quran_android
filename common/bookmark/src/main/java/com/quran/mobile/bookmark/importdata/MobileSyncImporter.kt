@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.importdata

import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.sync.LocalDataChangeNotifier
import com.quran.mobile.bookmark.sync.notifyLocalDataChanged
import com.quran.shared.persistence.input.ImportAyahBookmark
import com.quran.shared.persistence.input.ImportCollection
import com.quran.shared.persistence.input.ImportCollectionAyahBookmark
import com.quran.shared.persistence.input.ImportReadingBookmark
import com.quran.shared.persistence.input.ImportReadingSession
import com.quran.shared.persistence.input.PersistenceImportData
import com.quran.shared.persistence.input.PersistenceImportResult
import com.quran.shared.persistence.repository.importdata.PersistenceImportRepositoryImpl
import com.quran.shared.persistence.util.toPlatform
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

interface MobileSyncImporter {
  suspend fun importData(
    data: MobileSyncImportData,
    deleteExisting: Boolean = false
  ): MobileSyncImportResult
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MobileSyncImporterImpl @Inject constructor(
  mobileSyncDatabase: MobileSyncDatabase,
  private val localDataChangeNotifier: LocalDataChangeNotifier
) : MobileSyncImporter {

  private val importRepository = PersistenceImportRepositoryImpl(mobileSyncDatabase.database)

  override suspend fun importData(
    data: MobileSyncImportData,
    deleteExisting: Boolean
  ): MobileSyncImportResult {
    val result = importRepository
      .importData(data.toPersistenceImportData(), deleteExisting = deleteExisting)
      .toImportResult()
    if (!data.isEmpty() || deleteExisting) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
    return result
  }

  private fun MobileSyncImportData.toPersistenceImportData(): PersistenceImportData {
    return PersistenceImportData(
      bookmarks = bookmarks.map { bookmark ->
        ImportAyahBookmark(
          importId = bookmark.importId,
          sura = bookmark.sura,
          ayah = bookmark.ayah,
          lastUpdated = bookmark.timestampSeconds.toPlatformDateTime()
        )
      },
      collections = collections.map { collection ->
        ImportCollection(
          importId = collection.importId,
          name = collection.name,
          lastUpdated = collection.timestampSeconds.toPlatformDateTime()
        )
      },
      collectionBookmarks = collectionBookmarks.map { collectionBookmark ->
        ImportCollectionAyahBookmark(
          collectionImportId = collectionBookmark.collectionImportId,
          bookmarkImportId = collectionBookmark.bookmarkImportId,
          lastUpdated = collectionBookmark.timestampSeconds.toPlatformDateTime()
        )
      },
      readingSessions = readingSessions.map { readingSession ->
        ImportReadingSession(
          sura = readingSession.sura,
          ayah = readingSession.ayah,
          lastUpdated = readingSession.timestampSeconds.toPlatformDateTime()
        )
      },
      readingBookmark = readingBookmark?.toImportReadingBookmark()
    )
  }

  private fun MobileSyncImportReadingBookmark.toImportReadingBookmark(): ImportReadingBookmark {
    return when (this) {
      is MobileSyncImportReadingBookmark.Ayah -> ImportReadingBookmark.Ayah(
        sura = sura,
        ayah = ayah,
        lastUpdated = timestampSeconds.toPlatformDateTime()
      )
      is MobileSyncImportReadingBookmark.Page -> ImportReadingBookmark.Page(
        page = page,
        lastUpdated = timestampSeconds.toPlatformDateTime()
      )
    }
  }

  private fun PersistenceImportResult.toImportResult(): MobileSyncImportResult {
    return MobileSyncImportResult(
      bookmarksImported = bookmarksImported,
      collectionsImported = collectionsImported,
      collectionBookmarksImported = collectionBookmarksImported,
      readingSessionsImported = readingSessionsImported,
      readingBookmarkImported = readingBookmarkImported
    )
  }

  private fun Long.toPlatformDateTime() = Instant.fromEpochSeconds(this).toPlatform()
}
