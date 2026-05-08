@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.model

import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepositoryImpl
import com.quran.shared.persistence.util.fromPlatform
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import com.quran.shared.persistence.model.AyahReadingBookmark as SyncAyahReadingBookmark
import com.quran.shared.persistence.model.PageReadingBookmark as SyncPageReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmark as SyncReadingBookmark

@SingleIn(AppScope::class)
class ReadingBookmarksDaoImpl @Inject constructor(
  private val quranInfoProvider: () -> QuranInfo,
  mobileSyncDatabase: MobileSyncDatabase,
  appCoroutineScope: AppCoroutineScope
) : ReadingBookmarksDao {

  private val readingBookmarksRepository = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database)
  private val readingBookmarkState: StateFlow<ReadingBookmarkState> =
    readingBookmarksRepository.getReadingBookmarkFlow()
      .map { bookmark -> ReadingBookmarkState.Loaded(toReadingBookmark(bookmark)) }
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, ReadingBookmarkState.Loading)

  override fun readingBookmarkFlow(): Flow<ReadingBookmark?> {
    return readingBookmarkState
      .filterIsInstance<ReadingBookmarkState.Loaded>()
      .map { state -> state.bookmark }
      .distinctUntilChanged()
  }

  override suspend fun readingBookmark(): ReadingBookmark? {
    return withContext(Dispatchers.IO) {
      toReadingBookmark(readingBookmarksRepository.getReadingBookmark())
    }
  }

  override suspend fun setPageReadingBookmark(page: Int): Boolean {
    return withContext(Dispatchers.IO) {
      readingBookmarksRepository.addPageReadingBookmark(page)
      true
    }
  }

  override suspend fun setAyahReadingBookmark(suraAyah: SuraAyah): Boolean {
    return withContext(Dispatchers.IO) {
      readingBookmarksRepository.addAyahReadingBookmark(suraAyah.sura, suraAyah.ayah)
      true
    }
  }

  override suspend fun deleteReadingBookmark(): Boolean {
    return withContext(Dispatchers.IO) {
      readingBookmarksRepository.deleteReadingBookmark()
    }
  }

  override suspend fun isPageReadingBookmark(page: Int): Boolean {
    return withContext(Dispatchers.IO) {
      val bookmark = readingBookmarksRepository.getReadingBookmark()
      bookmark is SyncPageReadingBookmark && bookmark.page == page
    }
  }

  override suspend fun togglePageReadingBookmark(page: Int): Boolean {
    return withContext(Dispatchers.IO) {
      val bookmark = readingBookmarksRepository.getReadingBookmark()
      if (bookmark is SyncPageReadingBookmark && bookmark.page == page) {
        readingBookmarksRepository.deleteReadingBookmark()
        false
      } else {
        readingBookmarksRepository.addPageReadingBookmark(page)
        true
      }
    }
  }

  private fun toReadingBookmark(bookmark: SyncReadingBookmark?): ReadingBookmark? {
    return when (bookmark) {
      is SyncAyahReadingBookmark -> {
        val page = quranInfoProvider().getPageFromSuraAyah(bookmark.sura, bookmark.ayah)
        AyahReadingBookmark(
          sura = bookmark.sura,
          ayah = bookmark.ayah,
          page = page,
          timestamp = bookmark.lastUpdated.fromPlatform().toEpochMilliseconds() / 1000
        )
      }
      is SyncPageReadingBookmark -> PageReadingBookmark(
        page = bookmark.page,
        timestamp = bookmark.lastUpdated.fromPlatform().toEpochMilliseconds() / 1000
      )
      null -> null
    }
  }

  private sealed interface ReadingBookmarkState {
    data object Loading : ReadingBookmarkState
    data class Loaded(val bookmark: ReadingBookmark?) : ReadingBookmarkState
  }
}
