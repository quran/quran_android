package com.quran.mobile.bookmark.importdata

import android.content.Context
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.BackupReadingBookmark
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.mobile.bookmark.R
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.mobile.bookmark.time.legacyTimestampMillis
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject

class BookmarkBackupImportNormalizer @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val pageMapper: ReadingBookmarkPageMapper,
  private val timestampProvider: MobileSyncTimestampProvider
) {
  suspend fun normalize(data: BookmarkData): MobileSyncImportData {
    val sourcePageType = data.pageType ?: pageMapper.currentPageType()
    val importTimestampMillis = timestampProvider.nowEpochMillis()
    val collectionState = CollectionState.from(data.tags, importTimestampMillis)
    val bookmarkState = linkedMapOf<SuraAyah, BookmarkState>()
    var oldPageCollectionImportId: String? = null

    data.bookmarks.forEach { bookmark ->
      val normalizedBookmark = normalizeBookmark(bookmark, sourcePageType) ?: return@forEach
      val tagImportIds = bookmark.tags.mapNotNull { tagId ->
        collectionState.importIdForBackupTagId[tagId]
      }
      val importTagIds = if (normalizedBookmark.fromPageBookmark) {
        val collectionImportId = oldPageCollectionImportId
          ?: collectionState.oldPageBookmarkCollectionId(normalizedBookmark.timestamp)
            .also { oldPageCollectionImportId = it }
        tagImportIds + collectionImportId
      } else {
        tagImportIds
      }
      bookmarkState.addOrMerge(normalizedBookmark, importTagIds)
    }

    return MobileSyncImportData(
      bookmarks = bookmarkState.values.map { bookmark ->
        MobileSyncImportBookmark(
          importId = bookmark.importId,
          sura = bookmark.suraAyah.sura,
          ayah = bookmark.suraAyah.ayah,
          timestampMillis = bookmark.timestamp
        )
      },
      collections = collectionState.collections,
      collectionBookmarks = bookmarkState.values.flatMap { bookmark ->
        bookmark.collectionTimestamps.map { (collectionImportId, timestamp) ->
          MobileSyncImportCollectionBookmark(
            collectionImportId = collectionImportId,
            bookmarkImportId = bookmark.importId,
            timestampMillis = timestamp
          )
        }
      },
      readingSessions = normalizeRecentPages(data.recentPages, sourcePageType),
      readingBookmark = normalizeReadingBookmark(data.readingBookmark, sourcePageType)
    )
  }

  private fun CollectionState.oldPageBookmarkCollectionId(timestamp: Long): String {
    val oldPageBookmarksName = appContext.getString(R.string.old_page_bookmarks)
    return importIdForName[oldPageBookmarksName]
      ?: nextCollectionImportId().also { importId ->
        add(
          MobileSyncImportCollection(
            importId = importId,
            name = oldPageBookmarksName,
            timestampMillis = timestamp
          )
        )
      }
  }

  private fun normalizeBookmark(bookmark: Bookmark, pageType: String?): NormalizedBookmark? {
    val timestamp = bookmark.timestamp.legacyTimestampMillis()
    return if (bookmark.isPageBookmark()) {
      val suraAyah = pageMapper.sourcePageToSuraAyah(bookmark.page, pageType)
      NormalizedBookmark(
        suraAyah = suraAyah,
        timestamp = timestamp,
        fromPageBookmark = true
      )
    } else {
      val sura = bookmark.sura ?: return null
      val ayah = bookmark.ayah ?: return null
      if (!isValidSuraAyah(sura, ayah)) return null
      NormalizedBookmark(
        suraAyah = SuraAyah(sura, ayah),
        timestamp = timestamp,
        fromPageBookmark = false
      )
    }
  }

  private fun normalizeRecentPages(
    recentPages: List<RecentPage>,
    pageType: String?
  ): List<MobileSyncImportReadingSession> {
    val sessions = linkedMapOf<SuraAyah, MobileSyncImportReadingSession>()
    recentPages.forEach { recentPage ->
      val suraAyah = pageMapper.sourcePageToSuraAyah(recentPage.page, pageType)
      val existingSession = sessions[suraAyah]
      val timestamp = recentPage.timestamp.legacyTimestampMillis()
      if (existingSession == null || timestamp > existingSession.timestampMillis) {
        sessions[suraAyah] = MobileSyncImportReadingSession(
          sura = suraAyah.sura,
          ayah = suraAyah.ayah,
          timestampMillis = timestamp
        )
      }
    }
    return sessions.values.toList()
  }

  private fun normalizeReadingBookmark(
    readingBookmark: BackupReadingBookmark?,
    pageType: String?
  ): MobileSyncImportReadingBookmark? {
    return when (readingBookmark?.type) {
      BackupReadingBookmark.TYPE_AYAH -> {
        val sura = readingBookmark.sura ?: return null
        val ayah = readingBookmark.ayah ?: return null
        if (!isValidSuraAyah(sura, ayah)) return null
        MobileSyncImportReadingBookmark.Ayah(
          sura = sura,
          ayah = ayah,
          timestampMillis = readingBookmark.timestamp.legacyTimestampMillis()
        )
      }
      BackupReadingBookmark.TYPE_PAGE -> {
        val page = readingBookmark.page ?: return null
        MobileSyncImportReadingBookmark.Page(
          page = pageMapper.sourcePageToStoragePage(page, pageType),
          timestampMillis = readingBookmark.timestamp.legacyTimestampMillis()
        )
      }
      else -> null
    }
  }

  private fun MutableMap<SuraAyah, BookmarkState>.addOrMerge(
    normalizedBookmark: NormalizedBookmark,
    collectionImportIds: List<String>
  ) {
    val bookmark = getOrPut(normalizedBookmark.suraAyah) {
      BookmarkState(
        suraAyah = normalizedBookmark.suraAyah,
        timestamp = normalizedBookmark.timestamp,
        fromPageBookmark = normalizedBookmark.fromPageBookmark
      )
    }
    collectionImportIds.forEach { collectionImportId ->
      val timestamp = bookmark.collectionTimestamps[collectionImportId]
      if (timestamp == null || normalizedBookmark.timestamp > timestamp) {
        bookmark.collectionTimestamps[collectionImportId] = normalizedBookmark.timestamp
      }
    }
    bookmark.mergeTimestamp(normalizedBookmark)
  }

  private fun isValidSuraAyah(sura: Int, ayah: Int): Boolean = pageMapper.isValidSuraAyah(sura, ayah)

  private data class NormalizedBookmark(
    val suraAyah: SuraAyah,
    val timestamp: Long,
    val fromPageBookmark: Boolean
  )

  private data class BookmarkState(
    val suraAyah: SuraAyah,
    var timestamp: Long,
    var fromPageBookmark: Boolean,
    val collectionTimestamps: LinkedHashMap<String, Long> = linkedMapOf()
  ) {
    val importId: String = "bookmark-${suraAyah.sura}-${suraAyah.ayah}"

    fun mergeTimestamp(bookmark: NormalizedBookmark) {
      if (fromPageBookmark && !bookmark.fromPageBookmark) {
        timestamp = bookmark.timestamp
        fromPageBookmark = false
      } else if (fromPageBookmark == bookmark.fromPageBookmark && bookmark.timestamp > timestamp) {
        timestamp = bookmark.timestamp
      }
    }
  }

  private data class CollectionState(
    val importIdForBackupTagId: MutableMap<String, String> = mutableMapOf(),
    val importIdForName: MutableMap<String, String> = mutableMapOf(),
    val collections: MutableList<MobileSyncImportCollection> = mutableListOf(),
    val reservedCollectionImportIds: MutableSet<String> = mutableSetOf()
  ) {
    private var nextCollectionImportIndex = 0

    fun add(collection: MobileSyncImportCollection) {
      importIdForName[collection.name] = collection.importId
      reservedCollectionImportIds.add(collection.importId)
      collections.add(collection)
    }

    companion object {
      fun from(tags: List<Tag>, importTimestampMillis: Long): CollectionState {
        val state = CollectionState()
        state.reservedCollectionImportIds.addAll(tags.map { tag -> tag.id })
        tags.sortedWith(
          compareBy<Tag> { tag -> legacyTagNumber(tag.id) ?: Long.MAX_VALUE }
            .thenBy { tag -> tag.id }
        )
          .forEach { tag ->
            val name = tag.name
            if (name.isBlank()) return@forEach
            val existingImportId = state.importIdForName[name]
            if (existingImportId != null) {
              state.importIdForBackupTagId[tag.id] = existingImportId
            } else {
              val collection = MobileSyncImportCollection(
                importId = state.nextCollectionImportId(),
                name = name,
                timestampMillis = importTimestampMillis
              )
              state.importIdForBackupTagId[tag.id] = collection.importId
              state.add(collection)
            }
          }
        return state
      }

      private fun legacyTagNumber(tagId: String): Long? {
        return tagId.toLongOrNull()
      }
    }

    /**
     * Import IDs only correlate this import payload's collections to its bookmark links. They are
     * not mobile-sync local IDs, so do not reuse backup-provided tag IDs here.
     */
    fun nextCollectionImportId(): String {
      while (true) {
        val importId = "backup-collection-${nextCollectionImportIndex++}"
        if (reservedCollectionImportIds.add(importId)) {
          return importId
        }
      }
    }
  }
}
