@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.model

import com.quran.data.core.QuranInfo
import com.quran.data.dao.RecentPagesDao
import com.quran.data.dao.Settings
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.RecentPage
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.shared.persistence.model.ReadingSession
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepositoryImpl
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RecentPagesDaoImpl @Inject constructor(
  private val quranInfoProvider: () -> QuranInfo,
  private val settings: Settings,
  mobileSyncDatabase: MobileSyncDatabase,
  appCoroutineScope: AppCoroutineScope
) : RecentPagesDao {

  private val readingSessionsRepository = ReadingSessionsRepositoryImpl(mobileSyncDatabase.database)
  private val readingSessionsState: StateFlow<List<ReadingSession>?> =
    readingSessionsRepository.getReadingSessionsFlow()
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  private val pageTypeState: StateFlow<String?> =
    settings.preferencesFlow()
      .map { settings.pageType() }
      .onStart { emit(settings.pageType()) }
      .distinctUntilChanged()
      .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  override fun recentPagesFlow(): Flow<List<RecentPage>> {
    return combine(
      readingSessionsState.filterNotNull(),
      pageTypeState.filterNotNull()
    ) { sessions, _ ->
      recentPagesForSessions(sessions)
    }.distinctUntilChanged()
  }

  override suspend fun recentPages(): List<RecentPage> {
    return recentPagesForSessions(readingSessionsRepository.getReadingSessions())
  }

  override suspend fun addRecentPage(page: Int) {
    withContext(Dispatchers.IO) {
      val quranInfo = quranInfoProvider()
      addRecentPageInternal(page, quranInfo)
      pruneRecentSessions(quranInfo)
    }
  }

  override suspend fun replaceRecentRangeWithPage(deleteRangeStart: Int, deleteRangeEnd: Int, page: Int) {
    withContext(Dispatchers.IO) {
      val quranInfo = quranInfoProvider()
      deleteSessionsForPageRange(deleteRangeStart..deleteRangeEnd, quranInfo)
      addRecentPageInternal(page, quranInfo)
      pruneRecentSessions(quranInfo)
    }
  }

  override suspend fun removeRecentPages() {
    withContext(Dispatchers.IO) {
      sortedSessions().forEach { readingSessionsRepository.deleteReadingSession(it.sura, it.ayah) }
    }
  }

  override suspend fun replaceRecentPages(pages: List<RecentPage>) {
    withContext(Dispatchers.IO) {
      val quranInfo = quranInfoProvider()
      removeRecentPages()
      pages
        .take(MAX_RECENT_PAGES)
        .asReversed()
        .forEach { addRecentPageInternal(it.page, quranInfo) }
      pruneRecentSessions(quranInfo)
    }
  }

  override suspend fun removeRecentsForPage(page: Int) {
    withContext(Dispatchers.IO) {
      deleteSessionsForPageRange(page..page, quranInfoProvider())
    }
  }

  private suspend fun addRecentPageInternal(page: Int, quranInfo: QuranInfo) {
    if (quranInfo.isValidPage(page)) {
      deleteSessionsForPageRange(page..page, quranInfo)
      val bounds = quranInfo.getPageBounds(page)
      val sura = bounds[0]
      val ayah = bounds[1]
      readingSessionsRepository.addReadingSession(sura, ayah)
    }
  }

  private suspend fun deleteSessionsForPageRange(pageRange: IntRange, quranInfo: QuranInfo) {
    sortedSessions()
      .filter { session -> pageForSession(session, quranInfo)?.let { it in pageRange } == true }
      .forEach { readingSessionsRepository.deleteReadingSession(it.sura, it.ayah) }
  }

  private suspend fun pruneRecentSessions(quranInfo: QuranInfo) {
    val seenPages = mutableSetOf<Int>()
    sortedSessions().forEach { session ->
      val page = pageForSession(session, quranInfo)
      if (page == null || page in seenPages || seenPages.size >= MAX_RECENT_PAGES) {
        readingSessionsRepository.deleteReadingSession(session.sura, session.ayah)
      } else {
        seenPages.add(page)
      }
    }
  }

  private suspend fun sortedSessions(): List<ReadingSession> {
    return readingSessionsRepository.getReadingSessions()
      .sortedWith(
        compareByDescending<ReadingSession> { it.lastUpdated.toEpochMilliseconds() }
          .thenByDescending { it.localId.toLongOrNull() ?: Long.MIN_VALUE }
      )
  }

  private fun recentPagesForSessions(sessions: List<ReadingSession>): List<RecentPage> {
    val quranInfo = quranInfoProvider()
    val seenPages = mutableSetOf<Int>()
    return sessions
      .sortedWith(
        compareByDescending<ReadingSession> { it.lastUpdated.toEpochMilliseconds() }
          .thenByDescending { it.localId.toLongOrNull() ?: Long.MIN_VALUE }
      )
      .mapNotNull { session ->
        val page = pageForSession(session, quranInfo)
        if (page != null && seenPages.add(page)) {
          RecentPage(page, session.lastUpdated.toEpochMilliseconds() / 1000)
        } else {
          null
        }
      }
      .take(MAX_RECENT_PAGES)
  }

  private fun pageForSession(session: ReadingSession, quranInfo: QuranInfo): Int? {
    val page = quranInfo.getPageFromSuraAyah(session.sura, session.ayah)
    return page.takeIf { quranInfo.isValidPage(it) }
  }

  companion object {
    private const val MAX_RECENT_PAGES = 3
  }
}
