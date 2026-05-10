package com.quran.mobile.bookmark.importdata

import android.content.Context
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.BackupReadingBookmark
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.mobile.bookmark.R
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject

class BookmarkBackupImportNormalizer @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranInfo: QuranInfo,
  private val timestampProvider: MobileSyncTimestampProvider
) {
  fun normalize(data: BookmarkData): MobileSyncImportData {
    val importTimestampSeconds = timestampProvider.nowEpochSeconds()
    val collectionState = CollectionState.from(data.tags, importTimestampSeconds)
    val bookmarkState = linkedMapOf<SuraAyah, BookmarkState>()
    var oldPageCollectionImportId: String? = null

    data.bookmarks.forEach { bookmark ->
      val normalizedBookmark = normalizeBookmark(bookmark) ?: return@forEach
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
          timestampSeconds = bookmark.timestamp
        )
      },
      collections = collectionState.collections,
      collectionBookmarks = bookmarkState.values.flatMap { bookmark ->
        bookmark.collectionTimestamps.map { (collectionImportId, timestamp) ->
          MobileSyncImportCollectionBookmark(
            collectionImportId = collectionImportId,
            bookmarkImportId = bookmark.importId,
            timestampSeconds = timestamp
          )
        }
      },
      readingSessions = normalizeRecentPages(data.recentPages),
      readingBookmark = normalizeReadingBookmark(data.readingBookmark)
    )
  }

  private fun CollectionState.oldPageBookmarkCollectionId(timestamp: Long): String {
    val oldPageBookmarksName = appContext.getString(R.string.old_page_bookmarks)
    return importIdForName[oldPageBookmarksName]
      ?: OLD_PAGE_BOOKMARKS_COLLECTION_ID.also { importId ->
        add(
          MobileSyncImportCollection(
            importId = importId,
            name = oldPageBookmarksName,
            timestampSeconds = timestamp
          )
        )
      }
  }

  private fun normalizeBookmark(bookmark: Bookmark): NormalizedBookmark? {
    val timestamp = bookmark.timestamp.legacyBackupTimestampSeconds()
    return if (bookmark.isPageBookmark()) {
      val page = bookmark.page
      if (!quranInfo.isValidPage(page)) return null
      val bounds = quranInfo.getPageBounds(page)
      NormalizedBookmark(
        suraAyah = SuraAyah(bounds[0], bounds[1]),
        timestamp = timestamp,
        fromPageBookmark = true
      )
    } else {
      val sura = bookmark.sura ?: return null
      val ayah = bookmark.ayah ?: return null
      validPageForSuraAyah(sura, ayah) ?: return null
      NormalizedBookmark(
        suraAyah = SuraAyah(sura, ayah),
        timestamp = timestamp,
        fromPageBookmark = false
      )
    }
  }

  private fun normalizeRecentPages(recentPages: List<RecentPage>): List<MobileSyncImportReadingSession> {
    val sessions = linkedMapOf<SuraAyah, MobileSyncImportReadingSession>()
    recentPages.forEach { recentPage ->
      if (!quranInfo.isValidPage(recentPage.page)) return@forEach
      val bounds = quranInfo.getPageBounds(recentPage.page)
      val suraAyah = SuraAyah(bounds[0], bounds[1])
      val existingSession = sessions[suraAyah]
      val timestamp = recentPage.timestamp.legacyBackupTimestampSeconds()
      if (existingSession == null || timestamp > existingSession.timestampSeconds) {
        sessions[suraAyah] = MobileSyncImportReadingSession(
          sura = suraAyah.sura,
          ayah = suraAyah.ayah,
          timestampSeconds = timestamp
        )
      }
    }
    return sessions.values.toList()
  }

  private fun normalizeReadingBookmark(readingBookmark: BackupReadingBookmark?): MobileSyncImportReadingBookmark? {
    return when (readingBookmark?.type) {
      BackupReadingBookmark.TYPE_AYAH -> {
        val sura = readingBookmark.sura ?: return null
        val ayah = readingBookmark.ayah ?: return null
        validPageForSuraAyah(sura, ayah) ?: return null
        MobileSyncImportReadingBookmark.Ayah(
          sura = sura,
          ayah = ayah,
          timestampSeconds = readingBookmark.timestamp.legacyBackupTimestampSeconds()
        )
      }
      BackupReadingBookmark.TYPE_PAGE -> {
        val page = readingBookmark.page ?: return null
        if (!quranInfo.isValidPage(page)) return null
        MobileSyncImportReadingBookmark.Page(
          page = page,
          timestampSeconds = readingBookmark.timestamp.legacyBackupTimestampSeconds()
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

  private fun validPageForSuraAyah(sura: Int, ayah: Int): Int? {
    val numberOfAyahs = quranInfo.getNumberOfAyahs(sura)
    if (ayah !in 1..numberOfAyahs) return null
    return quranInfo.getPageFromSuraAyah(sura, ayah)
      .takeIf { page -> quranInfo.isValidPage(page) }
  }

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
    val importIdForBackupTagId: MutableMap<Long, String> = mutableMapOf(),
    val importIdForName: MutableMap<String, String> = mutableMapOf(),
    val collections: MutableList<MobileSyncImportCollection> = mutableListOf()
  ) {
    fun add(collection: MobileSyncImportCollection) {
      importIdForName[collection.name] = collection.importId
      collections.add(collection)
    }

    companion object {
      fun from(tags: List<Tag>, importTimestampSeconds: Long): CollectionState {
        val state = CollectionState()
        tags.sortedBy { tag -> tag.id }.forEach { tag ->
          val name = tag.name
          if (name.isBlank()) return@forEach
          val existingImportId = state.importIdForName[name]
          if (existingImportId != null) {
            state.importIdForBackupTagId[tag.id] = existingImportId
          } else {
            val collection = MobileSyncImportCollection(
              importId = "tag-${tag.id}",
              name = name,
              timestampSeconds = importTimestampSeconds
            )
            state.importIdForBackupTagId[tag.id] = collection.importId
            state.add(collection)
          }
        }
        return state
      }
    }
  }

  companion object {
    private const val OLD_PAGE_BOOKMARKS_COLLECTION_ID = "old-page-bookmarks"
  }
}

private const val MILLIS_TIMESTAMP_THRESHOLD = 10_000_000_000L

private fun Long.legacyBackupTimestampSeconds(): Long {
  // Legacy backup models can contain either epoch seconds or epoch milliseconds.
  return if (this > MILLIS_TIMESTAMP_THRESHOLD) this / 1000 else this
}
