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
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepository
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.quran.shared.persistence.model.AyahReadingBookmark as SyncAyahReadingBookmark
import com.quran.shared.persistence.model.EmptyReadingBookmark as SyncEmptyReadingBookmark
import com.quran.shared.persistence.model.PageReadingBookmark as SyncPageReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmark as SyncReadingBookmark

/**
 * Reading bookmark DAO backed by mobile-sync persistence.
 *
 * Page bookmarks are stored in canonical page coordinates and mapped to the current page type before they
 * are returned to app callers.
 *
 * @param pageMapper maps between current UI page coordinates and canonical storage page coordinates.
 * @param readingBookmarksRepository mobile-sync repository that owns persisted reading bookmark rows.
 * @param timestampProvider provides the timestamp assigned to locally written bookmarks.
 */
@SingleIn(AppScope::class)
class ReadingBookmarksDaoImpl @Inject constructor(
  private val pageMapper: ReadingBookmarkPageMapper,
  private val readingBookmarksRepository: ReadingBookmarksRepository,
  private val timestampProvider: MobileSyncTimestampProvider
) : ReadingBookmarksDao {
  override fun readingBookmarksFlow(): Flow<List<ReadingBookmark>> {
    // Keep this flow cold so new collectors start from the repository's current value instead of a
    // previously cached null. The IO dispatcher preserves the old off-main mapping behavior.
    return combine(
      readingBookmarksRepository.getReadingBookmarksFlow(),
      pageMapper.pageTypeFlow()
    ) { bookmarks, pageType ->
      bookmarks.map { toReadingBookmark(it, pageType) }
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.IO)
  }

  override suspend fun readingBookmarks(): List<ReadingBookmark> {
    return withContext(Dispatchers.IO) {
      readingBookmarksRepository.getReadingBookmarks()
        .map { toReadingBookmark(it, pageMapper.currentPageType()) }
    }
  }

  override suspend fun setPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    val timestamp = timestampProvider.now()
    val set = withContext(Dispatchers.IO) {
      readingBookmarksRepository.setPageReadingBookmark(slot.toSyncSlot(), pageMapper.currentPageToStoragePage(page), timestamp)
      true
    }
    return set
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
    return when (cleared) {
        is SyncEmptyReadingBookmark -> {
          EmptyReadingBookmark(
            slot = slot,
            timestamp = cleared.lastUpdated,
            name = cleared.name
          )
        }

      is SyncAyahReadingBookmark -> {
        AyahReadingBookmark(
          slot = slot,
          sura = cleared.sura,
          ayah = cleared.ayah,
          timestamp = cleared.lastUpdated,
          name = cleared.name
        )
      }

      is SyncPageReadingBookmark -> {
        PageReadingBookmark(
          slot = slot,
          page = cleared.page,
          timestamp = cleared.lastUpdated,
          name = cleared.name
        )
      }
    }
  }

  override suspend fun renameReadingBookmark(slot: ReadingBookmarkType, name: String?) {
    withContext(Dispatchers.IO) {
      readingBookmarksRepository.renameReadingBookmark(slot.toSyncSlot(), name)
    }
  }

  override suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    return withContext(Dispatchers.IO) {
      val storagePage = pageMapper.currentPageToStoragePage(page)

      val syncSlot = slot.toSyncSlot()
      readingBookmarksRepository.getReadingBookmarks()
        .any { it.slot == syncSlot && it is SyncPageReadingBookmark && it.page == storagePage }
    }
  }

  override suspend fun togglePageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean {
    val timestamp = timestampProvider.now()
    val (isBookmarked, _) = withContext(Dispatchers.IO) {
      val storagePage = pageMapper.currentPageToStoragePage(page)
      if (isPageReadingBookmark(slot, page)) {
        false to readingBookmarksRepository.clearReadingBookmark(slot.toSyncSlot())
      } else {
        readingBookmarksRepository.setPageReadingBookmark(slot.toSyncSlot(), storagePage, timestamp)
        true to true
      }
    }
    return isBookmarked
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
        page = pageMapper.storagePageToPage(bookmark.page, pageType),
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
    return withContext(Dispatchers.IO) {
      added.forEach { readingBookmark ->
        when (readingBookmark) {
          is AyahReadingBookmark ->
            readingBookmarksRepository.setAyahReadingBookmark(
              readingBookmark.slot.toSyncSlot(),
              readingBookmark.sura,
              readingBookmark.ayah
            )

          is PageReadingBookmark -> readingBookmarksRepository.setPageReadingBookmark(
            readingBookmark.slot.toSyncSlot(),
            readingBookmark.page
          )

          is EmptyReadingBookmark -> {}
        }
      }

      removed.forEach { readingBookmark ->
        readingBookmarksRepository.clearReadingBookmark(readingBookmark.slot.toSyncSlot())
      }
      true
    }
  }

  private fun ReadingBookmarkSlot.asBookmarkType(): ReadingBookmarkType {
    return when (this) {
      ReadingBookmarkSlot.GREEN -> ReadingBookmarkType.GREEN
      ReadingBookmarkSlot.PURPLE -> ReadingBookmarkType.PURPLE
      ReadingBookmarkSlot.BLUE -> ReadingBookmarkType.BLUE
    }
  }

  internal fun ReadingBookmarkType.toSyncSlot(): ReadingBookmarkSlot =
    when (this) {
      ReadingBookmarkType.GREEN -> ReadingBookmarkSlot.GREEN
      ReadingBookmarkType.PURPLE -> ReadingBookmarkSlot.PURPLE
      ReadingBookmarkType.BLUE -> ReadingBookmarkSlot.BLUE
    }
}
