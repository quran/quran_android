package com.quran.mobile.bookmark.model

import app.cash.turbine.test
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.data.warsh.WarshDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.sync.FakeLocalDataChangeNotifier
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepositoryImpl
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentPagesDaoImplTest {

  private lateinit var repository: ReadingSessionsRepositoryImpl
  private lateinit var quranInfo: QuranInfo
  private lateinit var dao: RecentPagesDaoImpl
  private lateinit var appCoroutineScope: AppCoroutineScope
  private lateinit var settings: FakeSettings
  private lateinit var localDataChangeNotifier: FakeLocalDataChangeNotifier
  private lateinit var timestampProvider: FakeMobileSyncTimestampProvider

  @Before
  fun setup() {
    val context = RuntimeEnvironment.getApplication().applicationContext as Context
    context.deleteDatabase("quran.db")
    val mobileSyncDatabase = MobileSyncDatabase(context)
    mobileSyncDatabase.database.reading_sessionsQueries.deleteAll()
    repository = ReadingSessionsRepositoryImpl(mobileSyncDatabase.database)
    quranInfo = QuranInfo(MadaniDataSource())
    settings = FakeSettings()
    appCoroutineScope = AppCoroutineScope()
    localDataChangeNotifier = FakeLocalDataChangeNotifier()
    timestampProvider = FakeMobileSyncTimestampProvider()
    dao = RecentPagesDaoImpl(
      quranInfoProvider = { quranInfo },
      settings = settings,
      readingSessionsRepository = repository,
      localDataChangeNotifier = localDataChangeNotifier,
      timestampProvider = timestampProvider,
      appCoroutineScope = appCoroutineScope
    )
  }

  @After
  fun tearDown() {
    if (::appCoroutineScope.isInitialized) {
      appCoroutineScope.cancel()
    }
  }

  @Test
  fun `recent pages are empty when no reading sessions exist`() = runTest {
    assertThat(dao.recentPages()).isEmpty()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(0)
  }

  @Test
  fun `add recent page stores first ayah on page as reading session`() = runTest {
    dao.addRecentPage(50)

    val recentPages = dao.recentPages()
    val sessions = repository.getReadingSessions()
    val pageBounds = quranInfo.getPageBounds(50)

    assertThat(recentPages.map { it.page }).containsExactly(50)
    assertThat(sessions).hasSize(1)
    assertThat(sessions.single().sura).isEqualTo(pageBounds[0])
    assertThat(sessions.single().ayah).isEqualTo(pageBounds[1])
    assertThat(recentPages.single().timestamp).isEqualTo(timestampProvider.timestampSeconds)
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(1)
  }

  @Test
  fun `recent pages are newest first and limited to three`() = runTest {
    dao.addRecentPage(1)
    dao.addRecentPage(2)
    dao.addRecentPage(3)
    dao.addRecentPage(4)
    dao.addRecentPage(5)

    val recentPages = dao.recentPages()

    assertThat(recentPages.map { it.page }).containsExactly(5, 4, 3).inOrder()
  }

  @Test
  fun `re-adding an existing page makes it newest`() = runTest {
    dao.addRecentPage(1)
    dao.addRecentPage(2)
    dao.addRecentPage(3)

    dao.addRecentPage(1)

    val recentPages = dao.recentPages()

    assertThat(recentPages.map { it.page }).containsExactly(1, 3, 2).inOrder()
  }

  @Test
  fun `sessions on the same page are deduplicated`() = runTest {
    repository.addReadingSession(1, 1)
    repository.addReadingSession(1, 2)

    val recentPages = dao.recentPages()

    assertThat(recentPages.map { it.page }).containsExactly(1)
  }

  @Test
  fun `replace recent range removes matching pages and adds final page`() = runTest {
    dao.addRecentPage(50)
    dao.addRecentPage(150)
    dao.addRecentPage(250)

    dao.replaceRecentRangeWithPage(100, 300, 200)

    val recentPages = dao.recentPages()

    assertThat(recentPages.map { it.page }).containsExactly(200, 50).inOrder()
  }

  @Test
  fun `replace recent pages preserves provided newest first order`() = runTest {
    dao.replaceRecentPages(
      listOf(
        RecentPage(10, 1),
        RecentPage(20, 1),
        RecentPage(30, 1),
        RecentPage(40, 1)
      )
    )

    val recentPages = dao.recentPages()

    assertThat(recentPages.map { it.page }).containsExactly(10, 20, 30).inOrder()
  }

  @Test
  fun `replace recent pages notifies once for multi step write`() = runTest {
    dao.addRecentPage(50)
    dao.addRecentPage(60)
    localDataChangeNotifier.reset()

    dao.replaceRecentPages(
      listOf(
        RecentPage(10, 1),
        RecentPage(20, 1),
        RecentPage(30, 1)
      )
    )

    assertThat(localDataChangeNotifier.updateCount).isEqualTo(1)
  }

  @Test
  fun `removing no recent pages does not notify`() = runTest {
    dao.removeRecentPages()

    assertThat(localDataChangeNotifier.updateCount).isEqualTo(0)
  }

  @Test
  fun `invalid reading sessions are ignored`() = runTest {
    repository.addReadingSession(99, 999)

    assertThat(dao.recentPages()).isEmpty()
  }

  @Test
  fun `recent pages flow remaps reading sessions when page type changes`() = runTest {
    dao.addRecentPage(48)

    dao.recentPagesFlow().test {
      var pages = awaitItem().map { it.page }
      while (pages != listOf(48)) {
        pages = awaitItem().map { it.page }
      }

      quranInfo = QuranInfo(WarshDataSource())
      settings.setPageType("warsh")

      assertThat(awaitItem().map { it.page }).containsExactly(49)
      cancelAndIgnoreRemainingEvents()
    }
  }

  private class FakeSettings : Settings {
    private val preferences = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var pageType = "madani"

    override suspend fun setVersion(version: Int) = Unit

    override suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean) = Unit

    override suspend fun lastPage(): Int = 1

    override suspend fun isNightMode(): Boolean = false

    override suspend fun nightModeTextBrightness(): Int = 0

    override suspend fun nightModeBackgroundBrightness(): Int = 0

    override suspend fun shouldShowHeaderFooter(): Boolean = false

    override suspend fun shouldShowBookmarks(): Boolean = false

    override suspend fun pageType(): String = pageType

    override suspend fun setPageType(pageType: String) {
      this.pageType = pageType
      preferences.emit("pageType")
    }

    override suspend fun showSidelines(): Boolean = false

    override suspend fun setShowSidelines(show: Boolean) = Unit

    override suspend fun showLineDividers(): Boolean = false

    override suspend fun setShouldShowLineDividers(show: Boolean) = Unit

    override suspend fun setAyahTextSize(value: Int) = Unit

    override suspend fun translationTextSize(): Int = 0

    override fun preferencesFlow(): Flow<String> = preferences
  }
}
