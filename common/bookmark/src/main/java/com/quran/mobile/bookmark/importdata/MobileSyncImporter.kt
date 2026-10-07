package com.quran.mobile.bookmark.importdata

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.R
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.di.qualifier.ApplicationContext
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
  fun destinationName(name: String): String {
    return if (name.trim().lowercase() in RESERVED_COLLECTION_NAMES) {
      appContext.getString(R.string.imported_collection_name, name)
    } else {
      name
    }
  }
  val collectionsByName = collections.groupBy { collection -> destinationName(collection.name) }

  return PersistenceImportData(
    collections = buildList {
      if (bookmarks.isNotEmpty()) {
        add(ImportCollection(
          name = FAVORITES_COLLECTION_NAME,
          lastUpdated = bookmarks.maxOf { it.timestampMillis }.toPlatformDateTime()
        ))
      }
      collectionsByName.forEach { (name, group) ->
        add(ImportCollection(
          name = name,
          lastUpdated = group.maxOf { it.timestampMillis }.toPlatformDateTime()
        ))
      }
    },
    collectionBookmarks = bookmarks.map { bookmark ->
      ImportCollectionAyahBookmark(
        collectionName = FAVORITES_COLLECTION_NAME,
        sura = bookmark.sura,
        ayah = bookmark.ayah,
        lastUpdated = bookmark.timestampMillis.toPlatformDateTime()
      )
    } + collectionBookmarks
      .groupBy { membership ->
        Triple(destinationName(membership.collectionName), membership.sura, membership.ayah)
      }
      .map { (key, memberships) ->
        val (collectionName, sura, ayah) = key
        ImportCollectionAyahBookmark(
          collectionName = collectionName,
          sura = sura,
          ayah = ayah,
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
    readingBookmarks = readingBookmarks.map { it.toImportReadingBookmark() }
  )
}

private const val FAVORITES_COLLECTION_NAME = "Favorites"

private val RESERVED_COLLECTION_NAMES = setOf(
  "favorites",
  "system:highlights:blue",
  "system:highlights:red",
  "system:highlights:green",
  "system:highlights:yellow",
  "system:highlights:purple"
)

private fun MobileSyncImportReadingBookmark.toImportReadingBookmark(): ImportReadingBookmark {
  return when (this) {
    is MobileSyncImportReadingBookmark.Ayah -> ImportReadingBookmark.Ayah(
      slot = slot,
      sura = sura,
      ayah = ayah,
      lastUpdated = timestampMillis.toPlatformDateTime(),
      name = name
    )
    is MobileSyncImportReadingBookmark.Page -> ImportReadingBookmark.Page(
      slot = slot,
      page = page,
      lastUpdated = timestampMillis.toPlatformDateTime(),
      name = name
    )
  }
}

fun PersistenceImportResult.toMobileSyncImportResult(): MobileSyncImportResult {
  return MobileSyncImportResult(
    bookmarksImported = bookmarksImported,
    collectionsImported = collectionsImported,
    collectionBookmarksImported = collectionBookmarksImported,
    readingSessionsImported = readingSessionsImported,
    readingBookmarkImported = readingBookmarksImported
  )
}

private fun Long.toPlatformDateTime() = Instant.fromEpochMilliseconds(this).toPlatform()
