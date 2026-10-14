package com.quran.mobile.bookmark.migration

import android.content.Context
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.mobile.bookmark.R
import com.quran.mobile.bookmark.importdata.MobileSyncImportBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollection
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollectionBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingSession
import com.quran.mobile.bookmark.legacy.LegacyBookmarkIds
import com.quran.mobile.bookmark.time.legacyTimestampMillis
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject

class LegacyBookmarkMigrationNormalizer @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranInfo: QuranInfo
) {
  fun normalize(snapshot: LegacyBookmarksSnapshot): MobileSyncImportData {
    val collectionState = CollectionState.from(snapshot.tags)
    val bookmarkState = linkedMapOf<SuraAyah, BookmarkState>()
    val oldPageBookmarksName by lazy { appContext.getString(R.string.old_page_bookmarks) }

    snapshot.bookmarks.forEach { bookmark ->
      val normalizedBookmark = normalizeBookmark(bookmark) ?: return@forEach
      val tagNames = bookmark.tags.mapNotNull { tagId ->
        collectionState.nameForLegacyTagId[tagId]
      }
      val collectionNames = if (normalizedBookmark.fromPageBookmark) {
        collectionState.addIfAbsent(oldPageBookmarksName, normalizedBookmark.timestamp)
        tagNames + oldPageBookmarksName
      } else {
        tagNames
      }
      bookmarkState.addOrMerge(normalizedBookmark, collectionNames)
    }

    val migrationBookmarks = bookmarkState.values.map { bookmark ->
      MobileSyncImportBookmark(
        sura = bookmark.suraAyah.sura,
        ayah = bookmark.suraAyah.ayah,
        timestampMillis = bookmark.timestamp
      )
    }

    val collectionBookmarks = bookmarkState.values.flatMap { bookmark ->
      bookmark.collectionTimestamps.map { (collectionName, timestamp) ->
        MobileSyncImportCollectionBookmark(
          collectionName = collectionName,
          sura = bookmark.suraAyah.sura,
          ayah = bookmark.suraAyah.ayah,
          timestampMillis = timestamp
        )
      }
    }

    return MobileSyncImportData(
      bookmarks = migrationBookmarks,
      collections = collectionState.collections.values.toList(),
      collectionBookmarks = collectionBookmarks,
      readingSessions = normalizeRecentPages(snapshot.recentPages)
    )
  }

  private fun normalizeBookmark(bookmark: Bookmark): NormalizedBookmark? {
    val timestamp = bookmark.timestamp.legacyTimestampMillis()
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
      sessions.getOrPut(suraAyah) {
        val timestamp = recentPage.timestamp
        MobileSyncImportReadingSession(
          sura = suraAyah.sura,
          ayah = suraAyah.ayah,
          timestampMillis = timestamp.toEpochMilliseconds()
        )
      }
    }
    return sessions.values.toList()
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
    val nameForLegacyTagId: MutableMap<String, String> = mutableMapOf(),
    val collections: LinkedHashMap<String, MobileSyncImportCollection> = linkedMapOf()
  ) {
    fun addIfAbsent(name: String, timestampMillis: Long) {
      collections.getOrPut(name) { MobileSyncImportCollection(name, timestampMillis) }
    }

    companion object {
      fun from(tags: List<LegacyBookmarkTag>): CollectionState {
        val state = CollectionState()
        tags.sortedBy { tag -> tag.id }.forEach { tag ->
          val name = tag.name
          if (name.isBlank()) return@forEach
          state.nameForLegacyTagId[LegacyBookmarkIds.tagId(tag.id)] = name
          state.addIfAbsent(name, tag.timestamp.legacyTimestampMillis())
        }
        return state
      }
    }
  }
}
