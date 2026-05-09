package com.quran.labs.androidquran.model.bookmark

import android.content.Context
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.R
import com.quran.mobile.bookmark.migration.MobileSyncMigrationBookmark
import com.quran.mobile.bookmark.migration.MobileSyncMigrationCollection
import com.quran.mobile.bookmark.migration.MobileSyncMigrationCollectionBookmark
import com.quran.mobile.bookmark.migration.MobileSyncMigrationData
import com.quran.mobile.bookmark.migration.MobileSyncMigrationReadingSession
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject

class LegacyBookmarkMigrationNormalizer @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranInfo: QuranInfo
) {
  fun normalize(snapshot: LegacyBookmarksSnapshot): MobileSyncMigrationData {
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
      MobileSyncMigrationBookmark(
        importId = bookmark.importId,
        sura = bookmark.suraAyah.sura,
        ayah = bookmark.suraAyah.ayah,
        timestampSeconds = bookmark.timestamp
      )
    }

    val collectionBookmarks = bookmarkState.values.flatMap { bookmark ->
      bookmark.collectionTimestamps.map { (collectionImportId, timestamp) ->
        MobileSyncMigrationCollectionBookmark(
          collectionImportId = collectionImportId,
          bookmarkImportId = bookmark.importId,
          timestampSeconds = timestamp
        )
      }
    }

    return MobileSyncMigrationData(
      bookmarks = migrationBookmarks,
      collections = collectionState.collections,
      collectionBookmarks = collectionBookmarks,
      readingSessions = normalizeRecentPages(snapshot.recentPages)
    )
  }

  private fun CollectionState.oldPageBookmarkCollectionId(timestamp: Long): String {
    val oldPageBookmarksName = appContext.getString(R.string.old_page_bookmarks)
    return importIdForName[oldPageBookmarksName]
      ?: OLD_PAGE_BOOKMARKS_COLLECTION_ID.also { importId ->
        add(
          MobileSyncMigrationCollection(
            importId = importId,
            name = oldPageBookmarksName,
            timestampSeconds = timestamp
          )
        )
      }
  }

  private fun normalizeBookmark(bookmark: Bookmark): NormalizedBookmark? {
    return if (bookmark.isPageBookmark()) {
      val page = bookmark.page
      if (!quranInfo.isValidPage(page)) return null
      val bounds = quranInfo.getPageBounds(page)
      NormalizedBookmark(
        suraAyah = SuraAyah(bounds[0], bounds[1]),
        timestamp = bookmark.timestamp,
        fromPageBookmark = true
      )
    } else {
      val sura = bookmark.sura ?: return null
      val ayah = bookmark.ayah ?: return null
      validPageForSuraAyah(sura, ayah) ?: return null
      NormalizedBookmark(
        suraAyah = SuraAyah(sura, ayah),
        timestamp = bookmark.timestamp,
        fromPageBookmark = false
      )
    }
  }

  private fun normalizeRecentPages(recentPages: List<RecentPage>): List<MobileSyncMigrationReadingSession> {
    val sessions = linkedMapOf<SuraAyah, MobileSyncMigrationReadingSession>()
    recentPages.forEach { recentPage ->
      if (!quranInfo.isValidPage(recentPage.page)) return@forEach
      val bounds = quranInfo.getPageBounds(recentPage.page)
      val suraAyah = SuraAyah(bounds[0], bounds[1])
      sessions.getOrPut(suraAyah) {
        MobileSyncMigrationReadingSession(
          sura = suraAyah.sura,
          ayah = suraAyah.ayah,
          timestampSeconds = recentPage.timestamp
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
    val importIdForLegacyTagId: MutableMap<Long, String> = mutableMapOf(),
    val importIdForName: MutableMap<String, String> = mutableMapOf(),
    val collections: MutableList<MobileSyncMigrationCollection> = mutableListOf()
  ) {
    fun add(collection: MobileSyncMigrationCollection) {
      importIdForName[collection.name] = collection.importId
      collections.add(collection)
    }

    companion object {
      fun from(tags: List<LegacyBookmarkTag>): CollectionState {
        val state = CollectionState()
        tags.sortedBy { tag -> tag.id }.forEach { tag ->
          val name = tag.name
          if (name.isBlank()) return@forEach
          val existingImportId = state.importIdForName[name]
          if (existingImportId != null) {
            state.importIdForLegacyTagId[tag.id] = existingImportId
          } else {
            val collection = MobileSyncMigrationCollection(
              importId = "tag-${tag.id}",
              name = name,
              timestampSeconds = tag.timestamp
            )
            state.importIdForLegacyTagId[tag.id] = collection.importId
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
