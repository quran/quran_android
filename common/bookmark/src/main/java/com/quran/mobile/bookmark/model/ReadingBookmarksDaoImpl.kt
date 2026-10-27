package com.quran.mobile.bookmark.model

import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.shared.persistence.model.Mushaf
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepository
import com.quran.shared.persistence.util.PlatformDateTime
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.quran.shared.persistence.model.AyahReadingBookmark as SyncAyahReadingBookmark
import com.quran.shared.persistence.model.EmptyReadingBookmark as SyncEmptyReadingBookmark
import com.quran.shared.persistence.model.PageReadingBookmark as SyncPageReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmark as SyncReadingBookmark

/**
 * Reading bookmark DAO backed by mobile-sync persistence.
 *
 * Page bookmarks are set and read in the current page type's mobile-sync [Mushaf], which maps them
 * between mushafs. Page types with no [Mushaf] are mapped by [ReadingBookmarkPageMapper].
 *
 * @param pageMapper maps between the current page type's pages and the pages mobile-sync takes.
 * @param readingBookmarksRepository mobile-sync repository that owns persisted reading bookmark rows.
 * @param timestampProvider provides the timestamp assigned to locally written bookmarks.
 */
@SingleIn(AppScope::class)
class ReadingBookmarksDaoImpl @Inject constructor(
  private val pageMapper: ReadingBookmarkPageMapper,
  private val readingBookmarksRepository: ReadingBookmarksRepository,
  private val timestampProvider: MobileSyncTimestampProvider
) : ReadingBookmarksDao {
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun readingBookmarksFlow(): Flow<List<ReadingBookmark>> {
    // Keep this flow cold so new collectors start from the repository's current value instead of a
    // previously cached null. The IO dispatcher preserves the old off-main mapping behavior.
    return pageMapper.pageTypeFlow()
      .flatMapLatest { pageType ->
        readingBookmarksRepository.getReadingBookmarksFlow(pageMapper.mushafFor(pageType))
          .map { bookmarks -> bookmarks.map { toReadingBookmark(it, pageType) } }
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.IO)
  }

  override suspend fun readingBookmarks(): List<ReadingBookmark> {
    return withContext(Dispatchers.IO) {
      val pageType = pageMapper.currentPageType()
      readingBookmarksRepository.getReadingBookmarks(pageMapper.mushafFor(pageType))
        .map { toReadingBookmark(it, pageType) }
    }
  }

  override suspend fun setPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    val timestamp = timestampProvider.now()
    return withContext(Dispatchers.IO) {
      setPageReadingBookmark(slot, currentMushafPage(page), timestamp)
    }
  }

  /** Sets [slot] to [mushafPage]; false when its mushaf has no such page, such as the IndoPak cover. */
  private suspend fun setPageReadingBookmark(
    slot: ReadingBookmarkType,
    mushafPage: MushafPage,
    timestamp: PlatformDateTime
  ): Boolean {
    return try {
      readingBookmarksRepository.setPageReadingBookmark(
        slot.toSyncSlot(),
        mushafPage.page,
        mushafPage.mushaf,
        timestamp
      )
      true
    } catch (_: IllegalArgumentException) {
      false
    }
  }

  private suspend fun currentMushafPage(page: Int): MushafPage {
    return pageMapper.pageToMushafPage(page, pageMapper.currentPageType())
  }

  override suspend fun setAyahReadingBookmark(slot: ReadingBookmarkType, suraAyah: SuraAyah): Boolean {
    val timestamp = timestampProvider.now()
    val set = withContext(Dispatchers.IO) {
      readingBookmarksRepository.setAyahReadingBookmark(slot.toSyncSlot(), suraAyah.sura, suraAyah.ayah, timestamp)
      true
    }
    return set
  }

  override suspend fun clearReadingBookmark(slot: ReadingBookmarkType): ReadingBookmark {
    val cleared = withContext(Dispatchers.IO) {
      readingBookmarksRepository.clearReadingBookmark(slot.toSyncSlot())
    }
    // a clear never leaves a page, which is the only part that depends on the page type
    return toReadingBookmark(cleared, pageMapper.currentPageType())
  }

  override suspend fun renameReadingBookmark(slot: ReadingBookmarkType, name: String?) {
    withContext(Dispatchers.IO) {
      val mushaf = pageMapper.mushafFor(pageMapper.currentPageType())
      readingBookmarksRepository.renameReadingBookmark(slot.toSyncSlot(), name, mushaf)
    }
  }

  override suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    return withContext(Dispatchers.IO) {
      isPageReadingBookmark(slot, page, pageMapper.currentPageType())
    }
  }

  /** Whether [slot] is on [page] of [pageType], compared in the pages [readingBookmarks] shows. */
  private suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int, pageType: String): Boolean {
    val syncSlot = slot.toSyncSlot()
    return readingBookmarksRepository.getReadingBookmarks(pageMapper.mushafFor(pageType))
      .any {
        it.slot == syncSlot &&
          it is SyncPageReadingBookmark &&
          pageMapper.mushafPageToPage(it.page, pageType) == page
      }
  }

  override suspend fun togglePageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    val timestamp = timestampProvider.now()
    return withContext(Dispatchers.IO) {
      val pageType = pageMapper.currentPageType()
      if (isPageReadingBookmark(slot, page, pageType)) {
        readingBookmarksRepository.clearReadingBookmark(slot.toSyncSlot())
        false
      } else {
        setPageReadingBookmark(slot, pageMapper.pageToMushafPage(page, pageType), timestamp)
      }
    }
  }

  private fun toReadingBookmark(bookmark: SyncReadingBookmark, pageType: String): ReadingBookmark {
    return when (bookmark) {
      is SyncAyahReadingBookmark -> {
        AyahReadingBookmark(
          slot = bookmark.slot.asBookmarkType(),
          sura = bookmark.sura,
          ayah = bookmark.ayah,
          timestamp = bookmark.lastUpdated,
          name = bookmark.name
        )
      }
      is SyncPageReadingBookmark -> PageReadingBookmark(
        slot = bookmark.slot.asBookmarkType(),
        page = pageMapper.mushafPageToPage(bookmark.page, pageType),
        timestamp = bookmark.lastUpdated,
        name = bookmark.name
      )
      is SyncEmptyReadingBookmark -> EmptyReadingBookmark(
        slot = bookmark.slot.asBookmarkType(),
        timestamp = bookmark.lastUpdated,
        name = bookmark.name
      )
    }
  }

  override suspend fun updateReadingBookmarks(
    ayah: SuraAyah,
    added: List<ReadingBookmark>,
    removed: List<ReadingBookmark>
  ): Boolean {
    val timestamp = timestampProvider.now()
    return withContext(Dispatchers.IO) {
      val pageType = pageMapper.currentPageType()
      var allSet = true
      added.forEach { readingBookmark ->
        when (readingBookmark) {
          is AyahReadingBookmark ->
            readingBookmarksRepository.setAyahReadingBookmark(
              readingBookmark.slot.toSyncSlot(),
              readingBookmark.sura,
              readingBookmark.ayah
            )

          is PageReadingBookmark -> {
            val isSet = setPageReadingBookmark(
              readingBookmark.slot,
              pageMapper.pageToMushafPage(readingBookmark.page, pageType),
              timestamp
            )
            allSet = allSet && isSet
          }

          is EmptyReadingBookmark -> {}
        }
      }

      removed.forEach { readingBookmark ->
        readingBookmarksRepository.clearReadingBookmark(readingBookmark.slot.toSyncSlot())
      }
      allSet
    }
  }

  private fun ReadingBookmarkSlot.asBookmarkType(): ReadingBookmarkType {
    return when (this) {
      ReadingBookmarkSlot.TEAL -> ReadingBookmarkType.TEAL
      ReadingBookmarkSlot.ORANGE -> ReadingBookmarkType.ORANGE
      ReadingBookmarkSlot.RED -> ReadingBookmarkType.RED
    }
  }

  internal fun ReadingBookmarkType.toSyncSlot(): ReadingBookmarkSlot =
    when (this) {
      ReadingBookmarkType.TEAL -> ReadingBookmarkSlot.TEAL
      ReadingBookmarkType.ORANGE -> ReadingBookmarkSlot.ORANGE
      ReadingBookmarkType.RED -> ReadingBookmarkSlot.RED
    }
}
