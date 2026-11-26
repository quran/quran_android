@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.model

import com.quran.data.core.QuranInfo
import com.quran.data.dao.RecentPagesDao
import com.quran.data.dao.Settings
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.RecentPage
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.shared.persistence.model.ReadingSession
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepository
import com.quran.shared.persistence.util.PlatformDateTime
import com.quran.shared.persistence.util.toPlatform
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
import kotlin.time.Instant

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class RecentPagesDaoImpl(
  private val quranInfoProvider: () -> QuranInfo,
  private val settings: Settings,
  private val readingSessionsRepository: ReadingSessionsRepository,
  private val timestampProvider: MobileSyncTimestampProvider,
  appCoroutineScope: AppCoroutineScope
) : RecentPagesDao {
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
    return withContext(Dispatchers.IO) {
      recentPagesForSessions(readingSessionsRepository.getReadingSessions())
    }
  }

  override suspend fun addRecentPage(page: Int) {
    val timestamp = timestampProvider.now()
    withContext(Dispatchers.IO) {
      updateRecentPagesWithPage(
        page = page,
        deleteRange = null,
        quranInfo = quranInfoProvider(),
        timestamp = timestamp
      )
    }
  }

  override suspend fun replaceRecentRangeWithPage(deleteRangeStart: Int, deleteRangeEnd: Int, page: Int) {
    val timestamp = timestampProvider.now()
    withContext(Dispatchers.IO) {
      updateRecentPagesWithPage(
        page = page,
        deleteRange = deleteRangeStart..deleteRangeEnd,
        quranInfo = quranInfoProvider(),
        timestamp = timestamp
      )
    }
  }

  override suspend fun removeRecentPages() {
    withContext(Dispatchers.IO) {
      removeRecentPagesInternal()
    }
  }

  override suspend fun replaceRecentPages(pages: List<RecentPage>) {
    withContext(Dispatchers.IO) {
      val quranInfo = quranInfoProvider()
      removeRecentPagesInternal()
      val seenPages = mutableSetOf<Int>()
      val pagesToInsert = pages
        .take(MAX_RECENT_PAGES)
        .filter { quranInfo.isValidPage(it.page) && seenPages.add(it.page) }
      for (i in pagesToInsert.lastIndex downTo 0) {
        val recentPage = pagesToInsert[i]
        val bounds = quranInfo.getPageBounds(recentPage.page)
        readingSessionsRepository.addReadingSession(bounds[0], bounds[1], recentPage.timestamp)
      }
    }
  }

  override suspend fun removeRecentsForPage(page: Int) {
    withContext(Dispatchers.IO) {
      deleteSessionsForPageRange(page..page, quranInfoProvider())
    }
  }

  private suspend fun updateRecentPagesWithPage(
    page: Int,
    deleteRange: IntRange?,
    quranInfo: QuranInfo,
    timestamp: PlatformDateTime
  ) {
    val sessions = sortedSessions()
    val validTargetPage = quranInfo.isValidPage(page)
    val seenPages = mutableSetOf<Int>()
    val surviving = ArrayList<ReadingSession>(sessions.size)

    for (session in sessions) {
      val sessionPage = pageForSession(session, quranInfo)
      val matchesDeleteRange = sessionPage != null && deleteRange != null && sessionPage in deleteRange
      val matchesTargetPage = validTargetPage && sessionPage == page
      if (sessionPage == null || matchesDeleteRange || matchesTargetPage || !seenPages.add(sessionPage)) {
        readingSessionsRepository.deleteReadingSession(session.sura, session.ayah)
      } else {
        surviving.add(session)
      }
    }

    if (validTargetPage) {
      val timestampMs = timestamp.toEpochMilliseconds()
      val newerExistingCount = surviving.count { it.lastUpdated.toEpochMilliseconds() > timestampMs }
      val maxSurviving = if (newerExistingCount >= MAX_RECENT_PAGES) {
        MAX_RECENT_PAGES
      } else {
        MAX_RECENT_PAGES - 1
      }
      for (i in maxSurviving until surviving.size) {
        val excess = surviving[i]
        readingSessionsRepository.deleteReadingSession(excess.sura, excess.ayah)
      }
      if (newerExistingCount < MAX_RECENT_PAGES) {
        val bounds = quranInfo.getPageBounds(page)
        readingSessionsRepository.addReadingSession(bounds[0], bounds[1], timestamp)
      }
    } else {
      for (i in MAX_RECENT_PAGES until surviving.size) {
        val excess = surviving[i]
        readingSessionsRepository.deleteReadingSession(excess.sura, excess.ayah)
      }
    }
  }

  private suspend fun deleteSessionsForPageRange(pageRange: IntRange, quranInfo: QuranInfo): Boolean {
    val sessionsToDelete = sortedSessions()
      .filter { session -> pageForSession(session, quranInfo)?.let { it in pageRange } == true }
    sessionsToDelete.forEach { readingSessionsRepository.deleteReadingSession(it.sura, it.ayah) }
    return sessionsToDelete.isNotEmpty()
  }

  private suspend fun removeRecentPagesInternal(): Boolean {
    val sessions = sortedSessions()
    sessions.forEach { readingSessionsRepository.deleteReadingSession(it.sura, it.ayah) }
    return sessions.isNotEmpty()
  }

  private suspend fun sortedSessions(): List<ReadingSession> {
    return readingSessionsRepository.getReadingSessions()
      .sortedWith(
        compareByDescending<ReadingSession> { it.lastUpdated.toEpochMilliseconds() }
          .thenByDescending { it.id.toLongOrNull() ?: Long.MIN_VALUE }
      )
  }

  private fun recentPagesForSessions(sessions: List<ReadingSession>): List<RecentPage> {
    val quranInfo = quranInfoProvider()
    val seenPages = mutableSetOf<Int>()
    return sessions
      .sortedWith(
        compareByDescending<ReadingSession> { it.lastUpdated.toEpochMilliseconds() }
          .thenByDescending { it.id.toLongOrNull() ?: Long.MIN_VALUE }
      )
      .mapNotNull { session ->
        val page = pageForSession(session, quranInfo)
        if (page != null && seenPages.add(page)) {
          RecentPage(page, session.lastUpdated)
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

  private fun Long.toPlatformDateTime(): PlatformDateTime {
    return Instant.fromEpochSeconds(this).toPlatform()
  }

  companion object {
    private const val MAX_RECENT_PAGES = 3
  }
}
