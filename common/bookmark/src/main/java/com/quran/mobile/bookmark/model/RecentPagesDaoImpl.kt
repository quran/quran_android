@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quran.mobile.bookmark.model

import com.quran.data.core.QuranInfo
import com.quran.data.dao.RecentPagesDao
import com.quran.data.dao.Settings
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.RecentPage
import com.quran.mobile.bookmark.sync.LocalDataChangeNotifier
import com.quran.mobile.bookmark.sync.notifyLocalDataChanged
import com.quran.mobile.bookmark.time.MobileSyncTimestampProvider
import com.quran.shared.persistence.model.ReadingSession
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepository
import com.quran.shared.persistence.util.PlatformDateTime
import com.quran.shared.persistence.util.toPlatform
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant
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
  private val readingSessionsRepository: ReadingSessionsRepository,
  private val localDataChangeNotifier: LocalDataChangeNotifier,
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
    return recentPagesForSessions(readingSessionsRepository.getReadingSessions())
  }

  override suspend fun addRecentPage(page: Int) {
    val timestamp = timestampProvider.now()
    val updated = withContext(Dispatchers.IO) {
      val quranInfo = quranInfoProvider()
      val added = addRecentPageInternal(page, quranInfo, timestamp)
      val pruned = pruneRecentSessions(quranInfo)
      added || pruned
    }
    if (updated) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
  }

  override suspend fun replaceRecentRangeWithPage(deleteRangeStart: Int, deleteRangeEnd: Int, page: Int) {
    val timestamp = timestampProvider.now()
    val updated = withContext(Dispatchers.IO) {
      val quranInfo = quranInfoProvider()
      val deleted = deleteSessionsForPageRange(deleteRangeStart..deleteRangeEnd, quranInfo)
      val added = addRecentPageInternal(page, quranInfo, timestamp)
      val pruned = pruneRecentSessions(quranInfo)
      deleted || added || pruned
    }
    if (updated) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
  }

  override suspend fun removeRecentPages() {
    val removed = withContext(Dispatchers.IO) {
      removeRecentPagesInternal()
    }
    if (removed) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
  }

  override suspend fun replaceRecentPages(pages: List<RecentPage>) {
    val updated = withContext(Dispatchers.IO) {
      val quranInfo = quranInfoProvider()
      var didWrite = removeRecentPagesInternal()
      pages
        .take(MAX_RECENT_PAGES)
        .asReversed()
        .forEach { recentPage ->
          didWrite = addRecentPageInternal(
            page = recentPage.page,
            quranInfo = quranInfo,
            timestamp = recentPage.timestamp.toPlatformDateTime()
          ) || didWrite
        }
      val pruned = pruneRecentSessions(quranInfo)
      didWrite || pruned
    }
    if (updated) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
  }

  override suspend fun removeRecentsForPage(page: Int) {
    val removed = withContext(Dispatchers.IO) {
      deleteSessionsForPageRange(page..page, quranInfoProvider())
    }
    if (removed) {
      localDataChangeNotifier.notifyLocalDataChanged()
    }
  }

  private suspend fun addRecentPageInternal(
    page: Int,
    quranInfo: QuranInfo,
    timestamp: PlatformDateTime
  ): Boolean {
    if (quranInfo.isValidPage(page)) {
      deleteSessionsForPageRange(page..page, quranInfo)
      val bounds = quranInfo.getPageBounds(page)
      val sura = bounds[0]
      val ayah = bounds[1]
      readingSessionsRepository.addReadingSession(sura, ayah, timestamp)
      return true
    }
    return false
  }

  private suspend fun deleteSessionsForPageRange(pageRange: IntRange, quranInfo: QuranInfo): Boolean {
    val sessionsToDelete = sortedSessions()
      .filter { session -> pageForSession(session, quranInfo)?.let { it in pageRange } == true }
    sessionsToDelete.forEach { readingSessionsRepository.deleteReadingSession(it.sura, it.ayah) }
    return sessionsToDelete.isNotEmpty()
  }

  private suspend fun pruneRecentSessions(quranInfo: QuranInfo): Boolean {
    val seenPages = mutableSetOf<Int>()
    var didWrite = false
    sortedSessions().forEach { session ->
      val page = pageForSession(session, quranInfo)
      if (page == null || page in seenPages || seenPages.size >= MAX_RECENT_PAGES) {
        readingSessionsRepository.deleteReadingSession(session.sura, session.ayah)
        didWrite = true
      } else {
        seenPages.add(page)
      }
    }
    return didWrite
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

  private fun Long.toPlatformDateTime(): PlatformDateTime {
    return Instant.fromEpochSeconds(this).toPlatform()
  }

  companion object {
    private const val MAX_RECENT_PAGES = 3
  }
}
