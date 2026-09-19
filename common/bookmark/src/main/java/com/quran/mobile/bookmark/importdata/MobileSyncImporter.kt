package com.quran.mobile.bookmark.importdata

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.R
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.di.qualifier.ApplicationContext
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
  @param:ApplicationContext private val appContext: Context
) : MobileSyncImporter {

  private val importRepository = PersistenceImportRepositoryImpl(mobileSyncDatabase.database)

  override suspend fun importData(
    data: MobileSyncImportData,
    deleteExisting: Boolean
  ): MobileSyncImportResult {
    return importRepository
      .importData(data.toPersistenceImportData(appContext), deleteExisting = deleteExisting)
      .toMobileSyncImportResult()
  }
}

fun MobileSyncImportData.toPersistenceImportData(appContext: Context): PersistenceImportData {
  val collectionsByName = collections.groupBy { collection ->
    if (collection.name.trim().lowercase() in RESERVED_COLLECTION_NAMES) {
      appContext.getString(R.string.imported_collection_name, collection.name)
    } else {
      collection.name
    }
  }
  val mergedCollectionIds = collectionsByName.values
    .flatMap { group -> group.map { it.importId to group.first().importId } }
    .toMap()
  return PersistenceImportData(
    bookmarks = bookmarks.map { bookmark ->
      ImportAyahBookmark(
        importId = bookmark.importId,
        sura = bookmark.sura,
        ayah = bookmark.ayah,
        lastUpdated = bookmark.timestampMillis.toPlatformDateTime()
      )
    },
    collections = collectionsByName.map { (name, group) ->
      ImportCollection(
        importId = group.first().importId,
        name = name,
        lastUpdated = group.maxOf { it.timestampMillis }.toPlatformDateTime()
      )
    },
    collectionBookmarks = collectionBookmarks
      .groupBy { membership ->
        val collectionId = mergedCollectionIds[membership.collectionImportId] ?: membership.collectionImportId
        collectionId to membership.bookmarkImportId
      }
      .map { (ids, memberships) ->
        ImportCollectionAyahBookmark(
          collectionImportId = ids.first,
          bookmarkImportId = ids.second,
          lastUpdated = memberships.maxOf { it.timestampMillis }.toPlatformDateTime()
        )
      },
    readingSessions = readingSessions.map { readingSession ->
      ImportReadingSession(
        sura = readingSession.sura,
        ayah = readingSession.ayah,
        lastUpdated = readingSession.timestampMillis.toPlatformDateTime()
      )
    },
    // TODO: needs 0.1.21
    // readingBookmarks = readingBookmarks.map { it.toImportReadingBookmark() }
  )
}

private val RESERVED_COLLECTION_NAMES = setOf(
  "favorites",
  "system:highlights:blue",
  "system:highlights:red",
  "system:highlights:green",
  "system:highlights:yellow",
  "system:highlights:purple"
)

/* TODO: needs 0.1.21
private fun MobileSyncImportReadingBookmark.toImportReadingBookmark(): ImportReadingBookmark {
  return when (this) {
    is MobileSyncImportReadingBookmark.Ayah -> ImportReadingBookmark.Ayah(
      slot = slot,
      sura = sura,
      ayah = ayah,
      lastUpdated = timestampMillis.toPlatformDateTime()
    )
    is MobileSyncImportReadingBookmark.Page -> ImportReadingBookmark.Page(
      slot = slot,
      page = page,
      lastUpdated = timestampMillis.toPlatformDateTime()
    )
  }
}
 */

fun PersistenceImportResult.toMobileSyncImportResult(): MobileSyncImportResult {
  return MobileSyncImportResult(
    bookmarksImported = bookmarksImported,
    collectionsImported = collectionsImported,
    collectionBookmarksImported = collectionBookmarksImported,
    readingSessionsImported = readingSessionsImported,
    readingBookmarkImported = 0 // TODO - needs 0.1.21: readingBookmarksImported
  )
}

private fun Long.toPlatformDateTime() = Instant.fromEpochMilliseconds(this).toPlatform()
