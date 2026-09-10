@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.model

import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepository
import com.quran.shared.persistence.util.fromPlatform
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.quran.shared.persistence.model.AyahReadingBookmark as SyncAyahReadingBookmark
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
  override fun readingBookmarkFlow(): Flow<ReadingBookmark?> {
    // Keep this flow cold so new collectors start from the repository's current value instead of a
    // previously cached null. The IO dispatcher preserves the old off-main mapping behavior.
    return combine(
      readingBookmarksRepository.getReadingBookmarkFlow(),
      pageMapper.pageTypeFlow()
    ) { bookmark, pageType ->
      toReadingBookmark(bookmark, pageType)
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.IO)
  }

  override suspend fun readingBookmark(): ReadingBookmark? {
    return withContext(Dispatchers.IO) {
      toReadingBookmark(
        readingBookmarksRepository.getReadingBookmark(),
        pageMapper.currentPageType()
      )
    }
  }

  override suspend fun setPageReadingBookmark(page: Int): Boolean {
    val timestamp = timestampProvider.now()
    val set = withContext(Dispatchers.IO) {
      readingBookmarksRepository.addPageReadingBookmark(pageMapper.currentPageToStoragePage(page), timestamp)
      true
    }
    return set
  }

  override suspend fun setAyahReadingBookmark(suraAyah: SuraAyah): Boolean {
    val timestamp = timestampProvider.now()
    val set = withContext(Dispatchers.IO) {
      readingBookmarksRepository.addAyahReadingBookmark(suraAyah.sura, suraAyah.ayah, timestamp)
      true
    }
    return set
  }

  override suspend fun deleteReadingBookmark(): Boolean {
    val deleted = withContext(Dispatchers.IO) {
      readingBookmarksRepository.deleteReadingBookmark()
    }
    return deleted
  }

  override suspend fun isPageReadingBookmark(page: Int): Boolean {
    return withContext(Dispatchers.IO) {
      val storagePage = pageMapper.currentPageToStoragePage(page)
      val bookmark = readingBookmarksRepository.getReadingBookmark()
      bookmark is SyncPageReadingBookmark && bookmark.page == storagePage
    }
  }

  override suspend fun togglePageReadingBookmark(page: Int): Boolean {
    val timestamp = timestampProvider.now()
    val (isBookmarked, _) = withContext(Dispatchers.IO) {
      val storagePage = pageMapper.currentPageToStoragePage(page)
      val bookmark = readingBookmarksRepository.getReadingBookmark()
      if (bookmark is SyncPageReadingBookmark && bookmark.page == storagePage) {
        false to readingBookmarksRepository.deleteReadingBookmark()
      } else {
        readingBookmarksRepository.addPageReadingBookmark(storagePage, timestamp)
        true to true
      }
    }
    return isBookmarked
  }

  private fun toReadingBookmark(bookmark: SyncReadingBookmark?, pageType: String): ReadingBookmark? {
    return when (bookmark) {
      is SyncAyahReadingBookmark -> {
        AyahReadingBookmark(
          sura = bookmark.sura,
          ayah = bookmark.ayah,
          timestamp = bookmark.lastUpdated.fromPlatform().toEpochMilliseconds() / 1000
        )
      }
      is SyncPageReadingBookmark -> PageReadingBookmark(
        page = pageMapper.storagePageToPage(bookmark.page, pageType),
        timestamp = bookmark.lastUpdated.fromPlatform().toEpochMilliseconds() / 1000
      )
      null -> null
    }
  }
}
