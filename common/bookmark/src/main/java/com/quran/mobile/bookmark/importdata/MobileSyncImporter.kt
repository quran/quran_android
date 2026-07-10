@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.importdata

import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.di.MobileSyncDatabase
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
  mobileSyncDatabase: MobileSyncDatabase
) : MobileSyncImporter {

  private val importRepository = PersistenceImportRepositoryImpl(mobileSyncDatabase.database)

  override suspend fun importData(
    data: MobileSyncImportData,
    deleteExisting: Boolean
  ): MobileSyncImportResult {
    return importRepository
      .importData(data.toPersistenceImportData(), deleteExisting = deleteExisting)
      .toMobileSyncImportResult()
  }
}

fun MobileSyncImportData.toPersistenceImportData(): PersistenceImportData {
  return PersistenceImportData(
    bookmarks = bookmarks.map { bookmark ->
      ImportAyahBookmark(
        importId = bookmark.importId,
        sura = bookmark.sura,
        ayah = bookmark.ayah,
        lastUpdated = bookmark.timestampMillis.toPlatformDateTime()
      )
    },
    collections = collections.map { collection ->
      ImportCollection(
        importId = collection.importId,
        name = collection.name,
        lastUpdated = collection.timestampMillis.toPlatformDateTime()
      )
    },
    collectionBookmarks = collectionBookmarks.map { collectionBookmark ->
      ImportCollectionAyahBookmark(
        collectionImportId = collectionBookmark.collectionImportId,
        bookmarkImportId = collectionBookmark.bookmarkImportId,
        lastUpdated = collectionBookmark.timestampMillis.toPlatformDateTime()
      )
    },
    readingSessions = readingSessions.map { readingSession ->
      ImportReadingSession(
        sura = readingSession.sura,
        ayah = readingSession.ayah,
        lastUpdated = readingSession.timestampMillis.toPlatformDateTime()
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
      lastUpdated = timestampMillis.toPlatformDateTime()
    )
    is MobileSyncImportReadingBookmark.Page -> ImportReadingBookmark.Page(
      page = page,
      lastUpdated = timestampMillis.toPlatformDateTime()
    )
  }
}

fun PersistenceImportResult.toMobileSyncImportResult(): MobileSyncImportResult {
  return MobileSyncImportResult(
    bookmarksImported = bookmarksImported,
    collectionsImported = collectionsImported,
    collectionBookmarksImported = collectionBookmarksImported,
    readingSessionsImported = readingSessionsImported,
    readingBookmarkImported = readingBookmarkImported
  )
}

private fun Long.toPlatformDateTime() = Instant.fromEpochMilliseconds(this).toPlatform()
