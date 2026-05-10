package com.quran.mobile.bookmark.importdata

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
import com.quran.data.model.audio.Qari
import com.quran.data.model.bookmark.BackupReadingBookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.QuranDataSource
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.data.warsh.WarshDataSource
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarkBackupImportNormalizerTest {

  private lateinit var normalizer: BookmarkBackupImportNormalizer
  private lateinit var pageMapper: ReadingBookmarkPageMapper
  private lateinit var settings: FakeSettings

  @Before
  fun setup() {
    val context = RuntimeEnvironment.getApplication().applicationContext as Context
    settings = FakeSettings()
    pageMapper = ReadingBookmarkPageMapper(
      settings = settings,
      pageProviders = mapOf(
        "madani" to TestPageProvider(MadaniDataSource()),
        "warsh" to TestPageProvider(WarshDataSource())
      ),
      fallbackPageType = "madani"
    )
    normalizer = BookmarkBackupImportNormalizer(
      appContext = context,
      pageMapper = pageMapper,
      timestampProvider = FakeMobileSyncTimestampProvider()
    )
  }

  @Test
  fun `normalizes page-based backup data from declared page type`() = runTest {
    val (warshPage, madaniPage) = firstWarshPageWithDifferentMadaniStorage()
    val warshBounds = QuranInfo(WarshDataSource()).getPageBounds(warshPage)

    val importData = normalizer.normalize(
      BookmarkData(
        recentPages = listOf(RecentPage(warshPage, 900)),
        readingBookmark = BackupReadingBookmark(
          type = BackupReadingBookmark.TYPE_PAGE,
          page = warshPage,
          timestamp = 800
        ),
        pageType = "warsh"
      )
    )

    assertThat(importData.bookmarks).isEmpty()
    assertThat(importData.readingSessions.single().sura).isEqualTo(warshBounds[0])
    assertThat(importData.readingSessions.single().ayah).isEqualTo(warshBounds[1])
    assertThat((importData.readingBookmark as MobileSyncImportReadingBookmark.Page).page)
      .isEqualTo(madaniPage)
  }

  @Test
  fun `normalizes legacy backup data from current page type when page type is missing`() = runTest {
    val (warshPage, madaniPage) = firstWarshPageWithDifferentMadaniStorage()
    settings.setPageType("warsh")

    val importData = normalizer.normalize(
      BookmarkData(
        readingBookmark = BackupReadingBookmark(
          type = BackupReadingBookmark.TYPE_PAGE,
          page = warshPage,
          timestamp = 800
        )
      )
    )

    assertThat((importData.readingBookmark as MobileSyncImportReadingBookmark.Page).page)
      .isEqualTo(madaniPage)
  }

  @Test
  fun `unknown source page type falls back to canonical page provider and clamps page`() = runTest {
    val madaniInfo = QuranInfo(MadaniDataSource())

    val importData = normalizer.normalize(
      BookmarkData(
        readingBookmark = BackupReadingBookmark(
          type = BackupReadingBookmark.TYPE_PAGE,
          page = 999,
          timestamp = 800
        ),
        pageType = "naskh"
      )
    )

    assertThat((importData.readingBookmark as MobileSyncImportReadingBookmark.Page).page)
      .isEqualTo(madaniInfo.numberOfPages)
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
