package com.quran.mobile.bookmark.model

import app.cash.turbine.test
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.QuranDataSource
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.data.warsh.WarshDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.sync.FakeLocalDataChangeNotifier
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.model.PageReadingBookmark as SyncPageReadingBookmark
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadingBookmarksDaoImplTest {

  private lateinit var repository: ReadingBookmarksRepositoryImpl
  private lateinit var quranInfo: QuranInfo
  private lateinit var dao: ReadingBookmarksDaoImpl
  private lateinit var pageMapper: ReadingBookmarkPageMapper
  private lateinit var settings: FakeSettings
  private lateinit var localDataChangeNotifier: FakeLocalDataChangeNotifier
  private lateinit var timestampProvider: FakeMobileSyncTimestampProvider

  @Before
  fun setup() {
    val context = RuntimeEnvironment.getApplication().applicationContext as Context
    context.deleteDatabase("quran.db")
    val mobileSyncDatabase = MobileSyncDatabase(context)
    repository = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database)
    quranInfo = QuranInfo(MadaniDataSource())
    settings = FakeSettings()
    localDataChangeNotifier = FakeLocalDataChangeNotifier()
    timestampProvider = FakeMobileSyncTimestampProvider()
    pageMapper = ReadingBookmarkPageMapper(
      settings = settings,
      pageProviders = mapOf(
        "madani" to TestPageProvider(MadaniDataSource()),
        "warsh" to TestPageProvider(WarshDataSource())
      ),
      fallbackPageType = "madani"
    )
    dao = ReadingBookmarksDaoImpl(
      pageMapper = pageMapper,
      readingBookmarksRepository = repository,
      localDataChangeNotifier = localDataChangeNotifier,
      timestampProvider = timestampProvider
    )
  }

  @Test
  fun `reading bookmark is null when no mobile sync reading bookmark exists`() = runTest {
    assertThat(dao.readingBookmark()).isNull()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(0)
  }

  @Test
  fun `set page reading bookmark stores mobile sync page reading bookmark`() = runTest {
    dao.setPageReadingBookmark(42)

    val bookmark = dao.readingBookmark() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(42)
    assertThat(bookmark.timestamp).isEqualTo(timestampProvider.timestampSeconds)
    assertThat(dao.isPageReadingBookmark(42)).isTrue()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(1)
  }

  @Test
  fun `set ayah reading bookmark maps page from quran info`() = runTest {
    val suraAyah = SuraAyah(2, 255)

    dao.setAyahReadingBookmark(suraAyah)

    val bookmark = dao.readingBookmark() as AyahReadingBookmark
    assertThat(bookmark.sura).isEqualTo(suraAyah.sura)
    assertThat(bookmark.ayah).isEqualTo(suraAyah.ayah)
    assertThat(bookmark.page).isEqualTo(quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah))
  }

  @Test
  fun `set page reading bookmark replaces existing ayah reading bookmark`() = runTest {
    dao.setAyahReadingBookmark(SuraAyah(2, 255))

    dao.setPageReadingBookmark(42)

    val bookmark = dao.readingBookmark() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(42)
  }

  @Test
  fun `toggle page reading bookmark deletes exact current page`() = runTest {
    dao.setPageReadingBookmark(42)

    val isBookmarked = dao.togglePageReadingBookmark(42)

    assertThat(isBookmarked).isFalse()
    assertThat(dao.readingBookmark()).isNull()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(2)
  }

  @Test
  fun `deleting no reading bookmark does not notify`() = runTest {
    val deleted = dao.deleteReadingBookmark()

    assertThat(deleted).isFalse()
    assertThat(localDataChangeNotifier.updateCount).isEqualTo(0)
  }

  @Test
  fun `toggle page reading bookmark replaces ayah bookmark on same page`() = runTest {
    val suraAyah = SuraAyah(2, 255)
    val page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
    dao.setAyahReadingBookmark(suraAyah)

    val isBookmarked = dao.togglePageReadingBookmark(page)

    assertThat(isBookmarked).isTrue()
    val bookmark = dao.readingBookmark() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(page)
  }

  @Test
  fun `reading bookmark flow emits external mobile sync writes`() = runTest {
    dao.readingBookmarkFlow().test {
      assertThat(awaitItem()).isNull()

      repository.addPageReadingBookmark(42)

      val bookmark = awaitItem() as PageReadingBookmark
      assertThat(bookmark.page).isEqualTo(42)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `set page reading bookmark stores canonical page when current page type differs`() = runTest {
    val (warshPage, madaniPage) = firstWarshPageWithDifferentMadaniStorage()
    settings.setPageType("warsh")

    dao.setPageReadingBookmark(warshPage)

    val syncBookmark = repository.getReadingBookmark() as SyncPageReadingBookmark
    assertThat(syncBookmark.page).isEqualTo(madaniPage)
    assertThat((dao.readingBookmark() as PageReadingBookmark).page).isEqualTo(warshPage)
    assertThat(dao.isPageReadingBookmark(warshPage)).isTrue()
  }

  @Test
  fun `reading bookmark flow remaps page bookmark when page type changes`() = runTest {
    val (warshPage, madaniPage) = firstWarshPageWithDifferentMadaniStorage()
    repository.addPageReadingBookmark(madaniPage)

    dao.readingBookmarkFlow().test {
      val madaniBookmark = awaitItem() as PageReadingBookmark
      assertThat(madaniBookmark.page).isEqualTo(madaniPage)

      settings.setPageType("warsh")

      val warshBookmark = awaitItem() as PageReadingBookmark
      assertThat(warshBookmark.page).isEqualTo(warshPage)
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun firstWarshPageWithDifferentMadaniStorage(): Pair<Int, Int> {
    val warshInfo = QuranInfo(WarshDataSource())
    val warshPage = (1..warshInfo.numberOfPages)
      .first { page ->
        if (!warshInfo.isValidPage(page)) return@first false
        val storagePage = pageMapper.pageToStoragePage(page, "warsh")
        storagePage != page && pageMapper.storagePageToPage(storagePage, "warsh") == page
      }
    return warshPage to pageMapper.pageToStoragePage(warshPage, "warsh")
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

  private class TestPageProvider(
    private val dataSource: QuranDataSource
  ) : PageProvider {
    override fun getDataSource(): QuranDataSource = dataSource

    override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator {
      throw UnsupportedOperationException()
    }

    override fun getImageVersion(): Int = 0

    override fun getImagesBaseUrl(): String = ""

    override fun getImagesZipBaseUrl(): String = ""

    override fun getPatchBaseUrl(): String = ""

    override fun getAyahInfoBaseUrl(): String = ""

    override fun getDatabasesBaseUrl(): String = ""

    override fun getAudioDatabasesBaseUrl(): String = ""

    override fun getAudioDirectoryName(): String = ""

    override fun getDatabaseDirectoryName(): String = ""

    override fun getAyahInfoDirectoryName(): String = ""

    override fun getImagesDirectoryName(): String = ""

    override fun getPreviewTitle(): Int = 0

    override fun getPreviewDescription(): Int = 0

    override fun getQaris(): List<Qari> = emptyList()

    override fun getDefaultQariId(): Int = 0
  }
}
