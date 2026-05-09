@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.migration

import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.shared.persistence.input.ImportAyahBookmark
import com.quran.shared.persistence.input.ImportCollection
import com.quran.shared.persistence.input.ImportCollectionAyahBookmark
import com.quran.shared.persistence.input.ImportReadingSession
import com.quran.shared.persistence.input.PersistenceImportData
import com.quran.shared.persistence.input.PersistenceImportResult
import com.quran.shared.persistence.repository.importdata.PersistenceImportRepositoryImpl
import com.quran.shared.persistence.util.toPlatform
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

interface MobileSyncMigrationImporter {
  suspend fun importData(data: MobileSyncMigrationData): MobileSyncMigrationImportResult
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MobileSyncMigrationImporterImpl @Inject constructor(
  mobileSyncDatabase: MobileSyncDatabase
) : MobileSyncMigrationImporter {

  private val importRepository = PersistenceImportRepositoryImpl(mobileSyncDatabase.database)

  override suspend fun importData(data: MobileSyncMigrationData): MobileSyncMigrationImportResult {
    return importRepository.importData(data.toPersistenceImportData()).toMigrationResult()
  }

  private fun MobileSyncMigrationData.toPersistenceImportData(): PersistenceImportData {
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
      }
    )
  }

  private fun PersistenceImportResult.toMigrationResult(): MobileSyncMigrationImportResult {
    return MobileSyncMigrationImportResult(
      bookmarksImported = bookmarksImported,
      collectionsImported = collectionsImported,
      collectionBookmarksImported = collectionBookmarksImported,
      readingSessionsImported = readingSessionsImported
    )
  }

  private fun Long.toPlatformDateTime() = Instant.fromEpochSeconds(this).toPlatform()
}
