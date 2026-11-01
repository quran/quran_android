package com.quran.mobile.bookmark.importdata

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
import com.quran.data.model.PageAppearance
import com.quran.data.model.bookmark.BackupReadingBookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.source.MushafType
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.data.warsh.WarshDataSource
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.bookmark.model.TestPageProvider
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
  private lateinit var settings: FakeSettings

  @Before
  fun setup() {
    settings = FakeSettings()
    normalizer = normalizer(
      // Shemerly's data source lives in quran-android-extras. Warsh's, a layout that differs from
      // madani's, stands in for it, so recent pages show which layout they are read in.
      pageProviders = mapOf(
        "madani" to TestPageProvider(MushafType.MADANI_1405, MadaniDataSource()),
        "shemerly_layout" to TestPageProvider(MushafType.SHEMERLY, WarshDataSource())
      ),
      fallbackPageType = "madani"
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
    val shemerlyBounds = QuranInfo(WarshDataSource()).getPageBounds(SHEMERLY_PAGE)

    val importData = normalizer.normalize(
      BookmarkData(
        recentPages = listOf(RecentPage(SHEMERLY_PAGE, Instant.fromEpochSeconds(900))),
        readingBookmarks = listOf(
          BackupReadingBookmark(
            slot = ReadingBookmarkType.ORANGE,
            type = BackupReadingBookmark.TYPE_PAGE,
            page = SHEMERLY_PAGE,
            timestamp = Instant.fromEpochSeconds(800),
            name = "Nightly reading"
          )
        ),
        pageType = "shemerly_layout"
      )
    )

    assertThat(importData.bookmarks).isEmpty()
    assertThat(importData.readingSessions.single().sura).isEqualTo(shemerlyBounds[0])
    assertThat(importData.readingSessions.single().ayah).isEqualTo(shemerlyBounds[1])
    val page = importData.readingBookmarks.single() as MobileSyncImportReadingBookmark.Page
    assertThat(page.page to page.mushaf).isEqualTo(SHEMERLY_PAGE to Mushaf.SHEMERLY)
    assertThat(page.name).isEqualTo("Nightly reading")
  }

  @Test
  fun `normalizes legacy backup data from current page type when page type is missing`() = runTest {
    settings.setPageType("shemerly_layout")

    val importData = normalizer.normalize(
      BookmarkData(
        readingBookmarks = listOf(
          BackupReadingBookmark(
            slot = ReadingBookmarkType.ORANGE,
            type = BackupReadingBookmark.TYPE_PAGE,
            page = SHEMERLY_PAGE,
            timestamp = Instant.fromEpochSeconds(800)
          )
        )
      )
    )

    val page = importData.readingBookmarks.single() as MobileSyncImportReadingBookmark.Page
    assertThat(page.page to page.mushaf).isEqualTo(SHEMERLY_PAGE to Mushaf.SHEMERLY)
  }

  @Test
  fun `an unknown source page type is read in the reference page type`() = runTest {
    val importData = normalizer.normalize(unknownPageTypeBackup())

    val madaniBounds = QuranInfo(MadaniDataSource()).getPageBounds(UNKNOWN_PAGE_TYPE_PAGE)
    assertThat(importData.readingSessions.single().sura to importData.readingSessions.single().ayah)
      .isEqualTo(madaniBounds[0] to madaniBounds[1])
    val page = importData.readingBookmarks.single() as MobileSyncImportReadingBookmark.Page
    assertThat(page.page to page.mushaf).isEqualTo(UNKNOWN_PAGE_TYPE_PAGE to Mushaf.MADANI_1405)
  }

  @Test
  fun `an unknown source page type is read in a riwayah build's own page type`() = runTest {
    // a Warsh build registers only warsh, so it is the reference page type
    val warshNormalizer = normalizer(
      pageProviders = mapOf("warsh" to TestPageProvider(MushafType.WARSH, WarshDataSource())),
      fallbackPageType = "warsh"
    )

    val importData = warshNormalizer.normalize(unknownPageTypeBackup())

    val warshBounds = QuranInfo(WarshDataSource()).getPageBounds(UNKNOWN_PAGE_TYPE_PAGE)
    assertThat(importData.readingSessions.single().sura to importData.readingSessions.single().ayah)
      .isEqualTo(warshBounds[0] to warshBounds[1])
    val page = importData.readingBookmarks.single() as MobileSyncImportReadingBookmark.Page
    assertThat(page.page to page.mushaf).isEqualTo(UNKNOWN_PAGE_TYPE_PAGE to Mushaf.WARSH)
  }

  /** A backup of a page type no build has, with a recent page and a page reading bookmark on it. */
  private fun unknownPageTypeBackup(): BookmarkData {
    return BookmarkData(
      recentPages = listOf(RecentPage(UNKNOWN_PAGE_TYPE_PAGE, Instant.fromEpochSeconds(900))),
      readingBookmarks = listOf(
        BackupReadingBookmark(
          slot = ReadingBookmarkType.ORANGE,
          type = BackupReadingBookmark.TYPE_PAGE,
          page = UNKNOWN_PAGE_TYPE_PAGE,
          timestamp = Instant.fromEpochSeconds(800)
        )
      ),
      pageType = "unknown"
    )
  }

  private fun normalizer(
    pageProviders: Map<String, PageProvider>,
    fallbackPageType: String
  ): BookmarkBackupImportNormalizer {
    return BookmarkBackupImportNormalizer(
      appContext = RuntimeEnvironment.getApplication().applicationContext as Context,
      pageMapper = ReadingBookmarkPageMapper(
        settings = settings,
        pageProviders = pageProviders,
        fallbackPageType = fallbackPageType
      ),
      timestampProvider = FakeMobileSyncTimestampProvider()
    )
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

  private companion object {
    const val SHEMERLY_PAGE = 300
    const val UNKNOWN_PAGE_TYPE_PAGE = 42
  }
}
