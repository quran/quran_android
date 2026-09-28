package com.quran.mobile.bookmark.importdata

import android.content.Context
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.BackupReadingBookmark
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.mobile.bookmark.R
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.mobile.bookmark.time.legacyTimestampMillis
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.shared.persistence.model.ReadingBookmarkSlot
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
    val oldPageBookmarksName by lazy { appContext.getString(R.string.old_page_bookmarks) }

    data.bookmarks.forEach { bookmark ->
      val normalizedBookmark = normalizeBookmark(bookmark, sourcePageType) ?: return@forEach
      val tagNames = bookmark.tags.mapNotNull { tagId ->
        collectionState.nameForBackupTagId[tagId]
      }
      val collectionNames = if (normalizedBookmark.fromPageBookmark) {
        collectionState.addIfAbsent(oldPageBookmarksName, normalizedBookmark.timestamp)
        tagNames + oldPageBookmarksName
      } else {
        tagNames
      }
      bookmarkState.addOrMerge(normalizedBookmark, collectionNames)
    }

    return MobileSyncImportData(
      bookmarks = bookmarkState.values.map { bookmark ->
        MobileSyncImportBookmark(
          sura = bookmark.suraAyah.sura,
          ayah = bookmark.suraAyah.ayah,
          timestampMillis = bookmark.timestamp
        )
      },
      collections = collectionState.collections.values.toList(),
      collectionBookmarks = bookmarkState.values.flatMap { bookmark ->
        bookmark.collectionTimestamps.map { (collectionName, timestamp) ->
          MobileSyncImportCollectionBookmark(
            collectionName = collectionName,
            sura = bookmark.suraAyah.sura,
            ayah = bookmark.suraAyah.ayah,
            timestampMillis = timestamp
          )
        }
      },
      readingSessions = normalizeRecentPages(data.recentPages, sourcePageType),
      readingBookmarks = normalizeReadingBookmarks(data.readingBookmarks, sourcePageType)
    )
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
      val timestamp = recentPage.timestamp
      if (existingSession == null) {
        sessions[suraAyah] = MobileSyncImportReadingSession(
          sura = suraAyah.sura,
          ayah = suraAyah.ayah,
          timestampMillis = timestamp.toEpochMilliseconds()
        )
      }
    }
    return sessions.values.toList()
  }

  private fun normalizeReadingBookmarks(
    readingBookmarks: List<BackupReadingBookmark>,
    pageType: String?
  ): List<MobileSyncImportReadingBookmark> {
    return readingBookmarks.mapNotNull { readingBookmark ->
      when (readingBookmark.type) {
        BackupReadingBookmark.TYPE_AYAH -> {
          val sura = readingBookmark.sura
          val ayah = readingBookmark.ayah
          if (sura == null || ayah == null || !isValidSuraAyah(sura, ayah)) {
            null
          } else {
            MobileSyncImportReadingBookmark.Ayah(
              slot = readingBookmark.slot.asBookmarkSlot(),
              sura = sura,
              ayah = ayah,
              timestampMillis = readingBookmark.timestamp.toEpochMilliseconds(),
              name = readingBookmark.name
            )
          }
        }

        BackupReadingBookmark.TYPE_PAGE -> {
          val page = readingBookmark.page
          if (page == null) {
            null
          } else {
            MobileSyncImportReadingBookmark.Page(
              slot = readingBookmark.slot.asBookmarkSlot(),
              page = pageMapper.sourcePageToStoragePage(page, pageType),
              timestampMillis = readingBookmark.timestamp.toEpochMilliseconds(),
              name = readingBookmark.name
            )
          }
        }

        else -> null
      }
    }
  }

  private fun ReadingBookmarkType.asBookmarkSlot(): ReadingBookmarkSlot {
    return when (this) {
      ReadingBookmarkType.GREEN -> ReadingBookmarkSlot.GREEN
      ReadingBookmarkType.PURPLE -> ReadingBookmarkSlot.PURPLE
      ReadingBookmarkType.BLUE -> ReadingBookmarkSlot.BLUE
    }
  }

  private fun MutableMap<SuraAyah, BookmarkState>.addOrMerge(
    normalizedBookmark: NormalizedBookmark,
    collectionNames: List<String>
  ) {
    val bookmark = getOrPut(normalizedBookmark.suraAyah) {
      BookmarkState(
        suraAyah = normalizedBookmark.suraAyah,
        timestamp = normalizedBookmark.timestamp,
        fromPageBookmark = normalizedBookmark.fromPageBookmark
      )
    }
    collectionNames.forEach { collectionName ->
      val timestamp = bookmark.collectionTimestamps[collectionName]
      if (timestamp == null || normalizedBookmark.timestamp > timestamp) {
        bookmark.collectionTimestamps[collectionName] = normalizedBookmark.timestamp
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
    val nameForBackupTagId: MutableMap<String, String> = mutableMapOf(),
    val collections: LinkedHashMap<String, MobileSyncImportCollection> = linkedMapOf()
  ) {
    fun addIfAbsent(name: String, timestampMillis: Long) {
      collections.getOrPut(name) { MobileSyncImportCollection(name, timestampMillis) }
    }

    companion object {
      fun from(tags: List<Tag>, importTimestampMillis: Long): CollectionState {
        val state = CollectionState()
        tags.sortedWith(
          compareBy<Tag> { tag -> legacyTagNumber(tag.id) ?: Long.MAX_VALUE }
            .thenBy { tag -> tag.id }
        )
          .forEach { tag ->
            val name = tag.name
            if (name.isBlank()) return@forEach
            state.nameForBackupTagId[tag.id] = name
            state.addIfAbsent(name, importTimestampMillis)
          }
        return state
      }

      private fun legacyTagNumber(tagId: String): Long? {
        return tagId.toLongOrNull()
      }
    }
  }
}
