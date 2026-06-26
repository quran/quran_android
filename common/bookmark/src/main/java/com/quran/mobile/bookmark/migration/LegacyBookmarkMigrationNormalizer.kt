package com.quran.mobile.bookmark.migration

import android.content.Context
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.LegacyBookmarkIds
import com.quran.data.model.bookmark.RecentPage
import com.quran.mobile.bookmark.R
import com.quran.mobile.bookmark.importdata.MobileSyncImportBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollection
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollectionBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingSession
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
    var oldPageCollectionImportId: String? = null

    snapshot.bookmarks.forEach { bookmark ->
      val normalizedBookmark = normalizeBookmark(bookmark) ?: return@forEach
      val tagImportIds = bookmark.tags.mapNotNull { tagId ->
        collectionState.importIdForLegacyTagId[tagId]
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

    val migrationBookmarks = bookmarkState.values.map { bookmark ->
      MobileSyncImportBookmark(
        importId = bookmark.importId,
        sura = bookmark.suraAyah.sura,
        ayah = bookmark.suraAyah.ayah,
        timestampMillis = bookmark.timestamp
      )
    }

    val collectionBookmarks = bookmarkState.values.flatMap { bookmark ->
      bookmark.collectionTimestamps.map { (collectionImportId, timestamp) ->
        MobileSyncImportCollectionBookmark(
          collectionImportId = collectionImportId,
          bookmarkImportId = bookmark.importId,
          timestampMillis = timestamp
        )
      }
    }

    return MobileSyncImportData(
      bookmarks = migrationBookmarks,
      collections = collectionState.collections,
      collectionBookmarks = collectionBookmarks,
      readingSessions = normalizeRecentPages(snapshot.recentPages)
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
        val timestamp = recentPage.timestamp.legacyTimestampMillis()
        MobileSyncImportReadingSession(
          sura = suraAyah.sura,
          ayah = suraAyah.ayah,
          timestampMillis = timestamp
        )
      }
    }
    return sessions.values.toList()
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
    val importIdForLegacyTagId: MutableMap<String, String> = mutableMapOf(),
    val importIdForName: MutableMap<String, String> = mutableMapOf(),
    val collections: MutableList<MobileSyncImportCollection> = mutableListOf()
  ) {
    private var nextCollectionImportIndex = 0

    fun add(collection: MobileSyncImportCollection) {
      importIdForName[collection.name] = collection.importId
      collections.add(collection)
    }

    companion object {
      fun from(tags: List<LegacyBookmarkTag>): CollectionState {
        val state = CollectionState()
        tags.sortedBy { tag -> tag.id }.forEach { tag ->
          val runtimeTagId = LegacyBookmarkIds.tagId(tag.id)
          val name = tag.name
          if (name.isBlank()) return@forEach
          val existingImportId = state.importIdForName[name]
          if (existingImportId != null) {
            state.importIdForLegacyTagId[runtimeTagId] = existingImportId
          } else {
            val collection = MobileSyncImportCollection(
              importId = state.nextCollectionImportId(),
              name = name,
              timestampMillis = tag.timestamp.legacyTimestampMillis()
            )
            state.importIdForLegacyTagId[runtimeTagId] = collection.importId
            state.add(collection)
          }
        }
        return state
      }
    }

    /**
     * Import IDs only correlate this migration payload's collections to its bookmark links. They
     * are not mobile-sync local IDs, so do not reuse old SQLite tag IDs here.
     */
    fun nextCollectionImportId(): String {
      return "legacy-collection-${nextCollectionImportIndex++}"
    }
  }
}
