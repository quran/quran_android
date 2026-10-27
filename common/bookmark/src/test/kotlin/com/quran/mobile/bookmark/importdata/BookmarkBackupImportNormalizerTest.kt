package com.quran.mobile.bookmark.importdata

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
import com.quran.data.model.PageAppearance
import com.quran.data.model.audio.Qari
import com.quran.data.model.bookmark.BackupReadingBookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.QuranDataSource
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.data.warsh.WarshDataSource
import com.quran.mobile.bookmark.model.MushafPage
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.model.Mushaf
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

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
      // Shemerly's and new_madani's data sources live in quran-android-extras. Any two layouts that
      // differ exercise Android's own mapping, so Warsh's stands in for Shemerly's and Madani's for
      // new_madani's.
      pageProviders = mapOf(
        "madani" to TestPageProvider(MadaniDataSource()),
        "new_madani" to TestPageProvider(MadaniDataSource()),
        "shemerly" to TestPageProvider(WarshDataSource())
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
  fun `backup reading bookmark colors match their mobile sync slots`() = runTest {
    val importData = normalizer.normalize(
      BookmarkData(
        readingBookmarks = listOf(
          ReadingBookmarkType.TEAL,
          ReadingBookmarkType.ORANGE,
          ReadingBookmarkType.RED
        ).mapIndexed { index, slot ->
          BackupReadingBookmark(
            slot = slot,
            type = BackupReadingBookmark.TYPE_AYAH,
            sura = 2,
            ayah = index + 1,
            timestamp = Instant.fromEpochSeconds(800)
          )
        }
      )
    )

    assertThat(importData.readingBookmarks.map { it as MobileSyncImportReadingBookmark.Ayah }
      .associate { it.slot to it.ayah }).containsExactly(
      ReadingBookmarkSlot.TEAL, 1,
      ReadingBookmarkSlot.ORANGE, 2,
      ReadingBookmarkSlot.RED, 3
    )
  }

  @Test
  fun `normalizes page-based backup data from declared page type`() = runTest {
    val (shemerlyPage, storedPage) = firstShemerlyPageStoredOnAnotherPage()
    val shemerlyBounds = QuranInfo(WarshDataSource()).getPageBounds(shemerlyPage)

    val importData = normalizer.normalize(
      BookmarkData(
        recentPages = listOf(RecentPage(shemerlyPage, Instant.fromEpochSeconds(900))),
        readingBookmarks = listOf(
          BackupReadingBookmark(
            slot = ReadingBookmarkType.ORANGE,
            type = BackupReadingBookmark.TYPE_PAGE,
            page = shemerlyPage,
            timestamp = Instant.fromEpochSeconds(800),
            name = "Nightly reading"
          )
        ),
        pageType = "shemerly"
      )
    )

    assertThat(importData.bookmarks).isEmpty()
    assertThat(importData.readingSessions.single().sura).isEqualTo(shemerlyBounds[0])
    assertThat(importData.readingSessions.single().ayah).isEqualTo(shemerlyBounds[1])
    val page = importData.readingBookmarks.single() as MobileSyncImportReadingBookmark.Page
    assertThat(MushafPage(page.page, page.mushaf)).isEqualTo(storedPage)
    assertThat(page.mushaf).isEqualTo(Mushaf.MADANI_1440)
    assertThat(page.name).isEqualTo("Nightly reading")
  }

  @Test
  fun `normalizes legacy backup data from current page type when page type is missing`() = runTest {
    val (shemerlyPage, storedPage) = firstShemerlyPageStoredOnAnotherPage()
    settings.setPageType("shemerly")

    val importData = normalizer.normalize(
      BookmarkData(
        readingBookmarks = listOf(
          BackupReadingBookmark(
            slot = ReadingBookmarkType.ORANGE,
            type = BackupReadingBookmark.TYPE_PAGE,
            page = shemerlyPage,
            timestamp = Instant.fromEpochSeconds(800)
          )
        )
      )
    )

    val page = importData.readingBookmarks.single() as MobileSyncImportReadingBookmark.Page
    assertThat(MushafPage(page.page, page.mushaf)).isEqualTo(storedPage)
  }

  @Test
  fun `backup pages of a page type with a mushaf keep their page in that mushaf`() = runTest {
    // naskh has no provider in this build: mobile-sync maps its pages, not this app
    val importData = normalizer.normalize(
      BookmarkData(
        readingBookmarks = listOf(
          BackupReadingBookmark(
            slot = ReadingBookmarkType.ORANGE,
            type = BackupReadingBookmark.TYPE_PAGE,
            page = 611,
            timestamp = Instant.fromEpochSeconds(800)
          )
        ),
        pageType = "naskh"
      )
    )

    val page = importData.readingBookmarks.single() as MobileSyncImportReadingBookmark.Page
    assertThat(MushafPage(page.page, page.mushaf)).isEqualTo(MushafPage(611, Mushaf.INDOPAK_15_LINES))
  }

  @Test
  fun `unknown source page type is read as madani pages`() = runTest {
    val importData = normalizer.normalize(
      BookmarkData(
        readingBookmarks = listOf(
          BackupReadingBookmark(
            slot = ReadingBookmarkType.ORANGE,
            type = BackupReadingBookmark.TYPE_PAGE,
            page = 42,
            timestamp = Instant.fromEpochSeconds(800)
          )
        ),
        pageType = "unknown"
      )
    )

    val page = importData.readingBookmarks.single() as MobileSyncImportReadingBookmark.Page
    assertThat(MushafPage(page.page, page.mushaf)).isEqualTo(MushafPage(42, Mushaf.MADANI_1405))
  }

  /** A Shemerly page whose new_madani page has a different number, and that new_madani page. */
  private fun firstShemerlyPageStoredOnAnotherPage(): Pair<Int, MushafPage> {
    val shemerlyInfo = QuranInfo(WarshDataSource())
    val shemerlyPage = (1..shemerlyInfo.numberOfPages)
      .first { page ->
        if (!shemerlyInfo.isValidPage(page)) return@first false
        val storedPage = pageMapper.pageToMushafPage(page, "shemerly").page
        storedPage != page && pageMapper.mushafPageToPage(storedPage, "shemerly") == page
      }
    return shemerlyPage to pageMapper.pageToMushafPage(shemerlyPage, "shemerly")
  }

  private class FakeSettings : Settings {
    private val preferences = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var pageType = "madani"

    override suspend fun setVersion(version: Int) = Unit

    override suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean) = Unit

    override suspend fun lastPage(): Int = 1

    override suspend fun pageAppearance(): PageAppearance = PageAppearance.Light

    override suspend fun pageTextBrightness(): Int = 0

    override suspend fun nightModeBackgroundBrightness(): Int = 0

    override suspend fun dayPageBackgroundColor(): Int? = null

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
