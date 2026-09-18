package com.quran.mobile.bookmark.model

import app.cash.turbine.test
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.QuranDataSource
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.data.warsh.WarshDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.model.PageReadingBookmark as SyncPageReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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
  private lateinit var timestampProvider: FakeMobileSyncTimestampProvider

  @Before
  fun setup() {
    val context = RuntimeEnvironment.getApplication().applicationContext as Context
    context.deleteDatabase("quran.db")
    val mobileSyncDatabase = MobileSyncDatabase(context)
    repository = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database)
    quranInfo = QuranInfo(MadaniDataSource())
    settings = FakeSettings()
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
      timestampProvider = timestampProvider
    )
  }

  @Test
  fun `reading bookmarks are empty when no mobile sync reading bookmark exists`() = runTest {
    assertThat(dao.readingBookmarks()).isEmpty()
  }

  @Test
  fun `set page reading bookmark stores mobile sync page reading bookmark`() = runTest {
    dao.setPageReadingBookmark(ReadingBookmarkType.TEAL, 42)

    val bookmark = dao.readingBookmarks().single() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(42)
    assertThat(bookmark.timestamp).isEqualTo(timestampProvider.now())
    assertThat(dao.isPageReadingBookmark(ReadingBookmarkType.TEAL, 42)).isTrue()
  }

  @Test
  fun `set ayah reading bookmark stores ayah reading bookmark`() = runTest {
    val suraAyah = SuraAyah(2, 255)

    dao.setAyahReadingBookmark(ReadingBookmarkType.TEAL, suraAyah)

    val bookmark = dao.readingBookmarks().single() as AyahReadingBookmark
    assertThat(bookmark.sura).isEqualTo(suraAyah.sura)
    assertThat(bookmark.ayah).isEqualTo(suraAyah.ayah)
    assertThat(bookmark.timestamp).isEqualTo(timestampProvider.now())
  }

  @Test
  fun `set page reading bookmark replaces existing ayah reading bookmark`() = runTest {
    dao.setAyahReadingBookmark(ReadingBookmarkType.TEAL, SuraAyah(2, 255))

    dao.setPageReadingBookmark(ReadingBookmarkType.TEAL, 42)

    val bookmark = dao.readingBookmarks().single() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(42)
  }

  @Test
  fun `replacing and clearing a slot preserves the other reading bookmarks`() = runTest {
    dao.setPageReadingBookmark(ReadingBookmarkType.CORAL, 42)
    dao.setAyahReadingBookmark(ReadingBookmarkType.TEAL, SuraAyah(2, 255))
    dao.setPageReadingBookmark(ReadingBookmarkType.INDIGO, 43)

    dao.setPageReadingBookmark(ReadingBookmarkType.TEAL, 50)

    assertThat(dao.readingBookmarks()).containsExactly(
      PageReadingBookmark(ReadingBookmarkType.CORAL, 42, timestampProvider.now()),
      PageReadingBookmark(ReadingBookmarkType.TEAL, 50, timestampProvider.now()),
      PageReadingBookmark(ReadingBookmarkType.INDIGO, 43, timestampProvider.now())
    )
    assertThat(dao.isPageReadingBookmark(ReadingBookmarkType.TEAL, 42)).isFalse()

    dao.clearReadingBookmark(ReadingBookmarkType.CORAL)

    assertThat(dao.readingBookmarks()).containsExactly(
      PageReadingBookmark(ReadingBookmarkType.TEAL, 50, timestampProvider.now()),
      PageReadingBookmark(ReadingBookmarkType.INDIGO, 43, timestampProvider.now())
    )
  }

  @Test
  fun `toggle page reading bookmark deletes exact current page`() = runTest {
    dao.setPageReadingBookmark(ReadingBookmarkType.TEAL, 42)

    val isBookmarked = dao.togglePageReadingBookmark(ReadingBookmarkType.TEAL, 42)

    assertThat(isBookmarked).isFalse()
    assertThat(dao.readingBookmarks()).isEmpty()
  }

  @Test
  fun `clearing an unused slot returns an empty bookmark`() = runTest {
    val cleared = dao.clearReadingBookmark(ReadingBookmarkType.TEAL)

    assertThat(cleared).isInstanceOf(EmptyReadingBookmark::class.java)
    assertThat(cleared.slot).isEqualTo(ReadingBookmarkType.TEAL)
    assertThat(dao.readingBookmarks()).isEmpty()
  }

  @Test
  fun `toggle page reading bookmark replaces ayah bookmark on same page`() = runTest {
    val suraAyah = SuraAyah(2, 255)
    val page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
    dao.setAyahReadingBookmark(ReadingBookmarkType.TEAL, suraAyah)

    val isBookmarked = dao.togglePageReadingBookmark(ReadingBookmarkType.TEAL, page)

    assertThat(isBookmarked).isTrue()
    val bookmark = dao.readingBookmarks().single() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(page)
  }

  @Test
  fun `reading bookmark flow emits external mobile sync writes`() = runTest {
    dao.readingBookmarksFlow().test {
      assertThat(awaitItem()).isEmpty()

      repository.setPageReadingBookmark(ReadingBookmarkSlot.TEAL, 42)

      val bookmark = awaitItem().single() as PageReadingBookmark
      assertThat(bookmark.page).isEqualTo(42)

      repository.clearReadingBookmark(ReadingBookmarkSlot.TEAL)

      assertThat(awaitItem()).isEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `set page reading bookmark stores canonical page when current page type differs`() = runTest {
    val (warshPage, madaniPage) = firstWarshPageWithDifferentMadaniStorage()
    settings.setPageType("warsh")

    dao.setPageReadingBookmark(ReadingBookmarkType.TEAL, warshPage)

    val syncBookmark = repository.getReadingBookmarks().single() as SyncPageReadingBookmark
    assertThat(syncBookmark.page).isEqualTo(madaniPage)
    assertThat((dao.readingBookmarks().single() as PageReadingBookmark).page).isEqualTo(warshPage)
    assertThat(dao.isPageReadingBookmark(ReadingBookmarkType.TEAL, warshPage)).isTrue()
  }

  @Test
  fun `reading bookmark flow remaps page bookmark when page type changes`() = runTest {
    val (warshPage, madaniPage) = firstWarshPageWithDifferentMadaniStorage()
    repository.setPageReadingBookmark(ReadingBookmarkSlot.TEAL, madaniPage)

    dao.readingBookmarksFlow().test {
      val madaniBookmark = awaitItem().single() as PageReadingBookmark
      assertThat(madaniBookmark.page).isEqualTo(madaniPage)

      settings.awaitPreferencesSubscriber()
      settings.setPageType("warsh")

      val warshBookmark = awaitItem().single() as PageReadingBookmark
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

    suspend fun awaitPreferencesSubscriber() {
      preferences.subscriptionCount.first { count -> count > 0 }
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
