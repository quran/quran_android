package com.quran.mobile.bookmark.model

import app.cash.turbine.test
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
import com.quran.data.model.PageAppearance
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.QuranDataSource
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.model.AyahReadingBookmark as SyncAyahReadingBookmark
import com.quran.shared.persistence.model.PageReadingBookmark as SyncPageReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmark as SyncReadingBookmark
import com.quran.shared.persistence.model.Mushaf
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepository
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepositoryImpl
import com.quran.shared.persistence.util.PlatformDateTime
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
  private lateinit var renameSpy: RenameMushafSpy
  private lateinit var quranInfo: QuranInfo
  private lateinit var dao: ReadingBookmarksDaoImpl
  private lateinit var settings: FakeSettings
  private lateinit var timestampProvider: FakeMobileSyncTimestampProvider

  @Before
  fun setup() {
    val context = RuntimeEnvironment.getApplication().applicationContext as Context
    context.deleteDatabase("user_data.db")
    val mobileSyncDatabase = MobileSyncDatabase(context)
    repository = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database.synced)
    renameSpy = RenameMushafSpy(repository)
    quranInfo = QuranInfo(MadaniDataSource())
    settings = FakeSettings()
    timestampProvider = FakeMobileSyncTimestampProvider()
    val pageMapper = ReadingBookmarkPageMapper(
      settings = settings,
      // mobile-sync maps every page type's pages, so only madani's layout (the reference for ayah
      // checks) may be read: reading another's would mean Android converted its pages.
      pageProviders = mapOf(
        "madani" to TestPageProvider(MadaniDataSource()),
        "new_madani" to TestPageProvider(dataSource = null),
        "shemerly" to TestPageProvider(dataSource = null),
        "warsh" to TestPageProvider(dataSource = null),
        "naskh" to TestPageProvider(dataSource = null),
        "tajweed" to TestPageProvider(dataSource = null)
      ),
      fallbackPageType = "madani"
    )
    dao = ReadingBookmarksDaoImpl(
      pageMapper = pageMapper,
      readingBookmarksRepository = renameSpy,
      timestampProvider = timestampProvider
    )
  }

  @Test
  fun `reading bookmarks are empty when no mobile sync reading bookmark exists`() = runTest {
    assertThat(dao.readingBookmarks()).isEmpty()
  }

  @Test
  fun `set page reading bookmark stores mobile sync page reading bookmark`() = runTest {
    dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, 42)

    val bookmark = dao.readingBookmarks().single() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(42)
    assertThat(bookmark.timestamp).isEqualTo(timestampProvider.now())
    assertThat(dao.isPageReadingBookmark(ReadingBookmarkType.ORANGE, 42)).isTrue()
    val syncBookmark = repository.getReadingBookmarks(Mushaf.MADANI_1405).single() as SyncPageReadingBookmark
    assertThat(syncBookmark.page to syncBookmark.mushaf).isEqualTo(42 to Mushaf.MADANI_1405)
  }

  @Test
  fun `set ayah reading bookmark stores ayah reading bookmark`() = runTest {
    val suraAyah = SuraAyah(2, 255)

    dao.setAyahReadingBookmark(ReadingBookmarkType.ORANGE, suraAyah)

    val bookmark = dao.readingBookmarks().single() as AyahReadingBookmark
    assertThat(bookmark.sura).isEqualTo(suraAyah.sura)
    assertThat(bookmark.ayah).isEqualTo(suraAyah.ayah)
    assertThat(bookmark.timestamp).isEqualTo(timestampProvider.now())
  }

  @Test
  fun `set page reading bookmark replaces existing ayah reading bookmark`() = runTest {
    dao.setAyahReadingBookmark(ReadingBookmarkType.ORANGE, SuraAyah(2, 255))

    dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, 42)

    val bookmark = dao.readingBookmarks().single() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(42)
  }

  @Test
  fun `replacing and clearing a slot preserves the other reading bookmarks`() = runTest {
    dao.setPageReadingBookmark(ReadingBookmarkType.TEAL, 42)
    dao.setAyahReadingBookmark(ReadingBookmarkType.ORANGE, SuraAyah(2, 255))
    dao.setPageReadingBookmark(ReadingBookmarkType.RED, 43)

    val syncBookmarks = repository.getReadingBookmarks(Mushaf.MADANI_1405)
    assertThat((syncBookmarks.single { it.slot == ReadingBookmarkSlot.TEAL } as SyncPageReadingBookmark).page)
      .isEqualTo(42)
    assertThat((syncBookmarks.single { it.slot == ReadingBookmarkSlot.ORANGE } as SyncAyahReadingBookmark).ayah)
      .isEqualTo(255)
    assertThat((syncBookmarks.single { it.slot == ReadingBookmarkSlot.RED } as SyncPageReadingBookmark).page)
      .isEqualTo(43)

    dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, 50)

    assertThat(dao.readingBookmarks()).containsExactly(
      PageReadingBookmark(ReadingBookmarkType.TEAL, 42, timestampProvider.now()),
      PageReadingBookmark(ReadingBookmarkType.ORANGE, 50, timestampProvider.now()),
      PageReadingBookmark(ReadingBookmarkType.RED, 43, timestampProvider.now())
    )
    assertThat(dao.isPageReadingBookmark(ReadingBookmarkType.ORANGE, 42)).isFalse()

    dao.clearReadingBookmark(ReadingBookmarkType.TEAL)

    assertThat(dao.readingBookmarks().placed()).containsExactly(
      PageReadingBookmark(ReadingBookmarkType.ORANGE, 50, timestampProvider.now()),
      PageReadingBookmark(ReadingBookmarkType.RED, 43, timestampProvider.now())
    )
    // the cleared slot keeps its row rather than disappearing - that row is where a renamed
    // pin's name lives, whether or not it is pointing anywhere
    assertThat(dao.readingBookmarks().single { it.slot == ReadingBookmarkType.TEAL })
      .isInstanceOf(EmptyReadingBookmark::class.java)
  }

  @Test
  fun `toggle page reading bookmark deletes exact current page`() = runTest {
    dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, 42)

    val isBookmarked = dao.togglePageReadingBookmark(ReadingBookmarkType.ORANGE, 42)

    assertThat(isBookmarked).isFalse()
    assertThat(dao.readingBookmarks().placed()).isEmpty()
  }

  @Test
  fun `a pin keeps its name when it is cleared, and when it was never placed`() = runTest {
    dao.setPageReadingBookmark(ReadingBookmarkType.TEAL, 42)
    dao.renameReadingBookmark(ReadingBookmarkType.TEAL, "Tafsir study")
    // naming a slot that has never been placed has to work too - Red starts out that way
    dao.renameReadingBookmark(ReadingBookmarkType.RED, "Memorizing")

    assertThat(dao.readingBookmarks().single { it.slot == ReadingBookmarkType.TEAL }.name)
      .isEqualTo("Tafsir study")
    val red = dao.readingBookmarks().single { it.slot == ReadingBookmarkType.RED }
    assertThat(red).isInstanceOf(EmptyReadingBookmark::class.java)
    assertThat(red.name).isEqualTo("Memorizing")

    dao.clearReadingBookmark(ReadingBookmarkType.TEAL)

    // the placement goes, the name stays
    val teal = dao.readingBookmarks().single { it.slot == ReadingBookmarkType.TEAL }
    assertThat(teal).isInstanceOf(EmptyReadingBookmark::class.java)
    assertThat(teal.name).isEqualTo("Tafsir study")
  }

  @Test
  fun `renaming with null restores the default name`() = runTest {
    dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, 42)
    dao.renameReadingBookmark(ReadingBookmarkType.ORANGE, "Nightly")

    dao.renameReadingBookmark(ReadingBookmarkType.ORANGE, null)

    assertThat(dao.readingBookmarks().single { it.slot == ReadingBookmarkType.ORANGE }.name).isNull()
  }

  @Test
  fun `clearing an unused slot returns an empty bookmark`() = runTest {
    val cleared = dao.clearReadingBookmark(ReadingBookmarkType.ORANGE)

    assertThat(cleared).isInstanceOf(EmptyReadingBookmark::class.java)
    assertThat(cleared.slot).isEqualTo(ReadingBookmarkType.ORANGE)
    assertThat(dao.readingBookmarks().placed()).isEmpty()
  }

  @Test
  fun `toggle page reading bookmark replaces ayah bookmark on same page`() = runTest {
    val suraAyah = SuraAyah(2, 255)
    val page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
    dao.setAyahReadingBookmark(ReadingBookmarkType.ORANGE, suraAyah)

    val isBookmarked = dao.togglePageReadingBookmark(ReadingBookmarkType.ORANGE, page)

    assertThat(isBookmarked).isTrue()
    val bookmark = dao.readingBookmarks().single() as PageReadingBookmark
    assertThat(bookmark.page).isEqualTo(page)
  }

  @Test
  fun `reading bookmark flow emits external mobile sync writes`() = runTest {
    dao.readingBookmarksFlow().test {
      assertThat(awaitItem()).isEmpty()

      repository.setPageReadingBookmark(ReadingBookmarkSlot.ORANGE, 42, Mushaf.MADANI_1405)

      val bookmark = awaitItem().single() as PageReadingBookmark
      assertThat(bookmark.page).isEqualTo(42)

      repository.clearReadingBookmark(ReadingBookmarkSlot.ORANGE)

      assertThat(awaitItem().placed()).isEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `setting a naskh page passes the same page in the 15-line IndoPak mushaf`() = runTest {
    settings.setPageType("naskh")

    val isSet = dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, 611)

    assertThat(isSet).isTrue()
    val syncBookmark = repository.getReadingBookmarks(Mushaf.INDOPAK_15_LINES).single() as SyncPageReadingBookmark
    assertThat(syncBookmark.page to syncBookmark.mushaf).isEqualTo(611 to Mushaf.INDOPAK_15_LINES)
    assertThat((dao.readingBookmarks().single() as PageReadingBookmark).page).isEqualTo(611)
    assertThat(dao.isPageReadingBookmark(ReadingBookmarkType.ORANGE, 611)).isTrue()
  }

  @Test
  fun `a page the mushaf does not have is not set`() = runTest {
    settings.setPageType("naskh")

    // page 1 of the 15-line IndoPak mushaf is its cover
    assertThat(dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, 1)).isFalse()
    assertThat(dao.togglePageReadingBookmark(ReadingBookmarkType.ORANGE, 1)).isFalse()

    assertThat(dao.readingBookmarks().placed()).isEmpty()
  }

  @Test
  fun `reading returns pages of the current page type's mushaf`() = runTest {
    repository.setPageReadingBookmark(ReadingBookmarkSlot.ORANGE, MADANI_PAGE, Mushaf.MADANI_1405)
    settings.setPageType("naskh")

    val bookmark = dao.readingBookmarks().single() as PageReadingBookmark

    assertThat(bookmark.page).isEqualTo(indoPakPageForMadaniPage())
  }

  @Test
  fun `reading bookmark flow rereads in the new mushaf when page type changes`() = runTest {
    repository.setPageReadingBookmark(ReadingBookmarkSlot.ORANGE, MADANI_PAGE, Mushaf.MADANI_1405)

    dao.readingBookmarksFlow().test {
      val madaniBookmark = awaitItem().single() as PageReadingBookmark
      assertThat(madaniBookmark.page).isEqualTo(MADANI_PAGE)

      settings.awaitPreferencesSubscriber()
      settings.setPageType("naskh")

      val indoPakBookmark = awaitItem().single() as PageReadingBookmark
      assertThat(indoPakBookmark.page).isEqualTo(indoPakPageForMadaniPage())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a shemerly page passes through unchanged in the Shemerly mushaf`() = runTest {
    settings.setPageType("shemerly")

    dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, SHEMERLY_PAGE)

    val syncBookmark = repository.getReadingBookmarks(Mushaf.SHEMERLY).single() as SyncPageReadingBookmark
    assertThat(syncBookmark.page to syncBookmark.mushaf).isEqualTo(SHEMERLY_PAGE to Mushaf.SHEMERLY)
    assertThat((dao.readingBookmarks().single() as PageReadingBookmark).page).isEqualTo(SHEMERLY_PAGE)
    assertThat(dao.isPageReadingBookmark(ReadingBookmarkType.ORANGE, SHEMERLY_PAGE)).isTrue()
  }

  @Test
  fun `shemerly reads pages set in another mushaf as mobile-sync maps them`() = runTest {
    repository.setPageReadingBookmark(ReadingBookmarkSlot.ORANGE, MADANI_PAGE, Mushaf.MADANI_1405)
    settings.setPageType("shemerly")

    val bookmark = dao.readingBookmarks().single() as PageReadingBookmark

    val shemerlyPage = (repository.getReadingBookmarks(Mushaf.SHEMERLY).single() as SyncPageReadingBookmark).page
    // pages differ between the two, so equal pages would mean nothing was mapped
    assertThat(shemerlyPage).isNotEqualTo(MADANI_PAGE)
    assertThat(bookmark.page).isEqualTo(shemerlyPage)
  }

  @Test
  fun `the shemerly page check matches the page the flow shows`() = runTest {
    // mobile-sync stores Shemerly page 9 on a 1440 page that reads back as another Shemerly page
    val setPage = 9
    settings.setPageType("shemerly")

    dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, setPage)

    val shownPage = (dao.readingBookmarksFlow().first().single() as PageReadingBookmark).page
    assertThat(shownPage).isNotEqualTo(setPage)
    assertThat(dao.isPageReadingBookmark(ReadingBookmarkType.ORANGE, shownPage)).isTrue()
    assertThat(dao.isPageReadingBookmark(ReadingBookmarkType.ORANGE, setPage)).isFalse()
    assertThat(dao.togglePageReadingBookmark(ReadingBookmarkType.ORANGE, shownPage)).isFalse()
    assertThat(dao.readingBookmarks().placed()).isEmpty()
  }

  @Test
  fun `a warsh page passes through unchanged in the Warsh mushaf`() = runTest {
    settings.setPageType("warsh")

    dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, 42)

    val syncBookmark = repository.getReadingBookmarks(Mushaf.WARSH).single() as SyncPageReadingBookmark
    assertThat(syncBookmark.page to syncBookmark.mushaf).isEqualTo(42 to Mushaf.WARSH)
    assertThat((dao.readingBookmarks().single() as PageReadingBookmark).page).isEqualTo(42)
    assertThat(dao.isPageReadingBookmark(ReadingBookmarkType.ORANGE, 42)).isTrue()
  }

  @Test
  fun `a tajweed page passes through unchanged in the 1405 mushaf`() = runTest {
    settings.setPageType("tajweed")

    dao.setPageReadingBookmark(ReadingBookmarkType.ORANGE, 42)

    val syncBookmark = repository.getReadingBookmarks(Mushaf.MADANI_1405).single() as SyncPageReadingBookmark
    assertThat(syncBookmark.page to syncBookmark.mushaf).isEqualTo(42 to Mushaf.MADANI_1405)
    assertThat((dao.readingBookmarks().single() as PageReadingBookmark).page).isEqualTo(42)
  }

  @Test
  fun `rename passes the current page type's mushaf`() = runTest {
    settings.setPageType("naskh")

    dao.renameReadingBookmark(ReadingBookmarkType.ORANGE, "Nightly")

    assertThat(renameSpy.renameMushafs).containsExactly(Mushaf.INDOPAK_15_LINES)
  }

  @Test
  fun `updating reading bookmarks reports a page the mushaf does not have`() = runTest {
    settings.setPageType("naskh")

    val isUpdated = dao.updateReadingBookmarks(
      ayah = SuraAyah(1, 1),
      added = listOf(PageReadingBookmark(ReadingBookmarkType.ORANGE, 1, timestampProvider.now())),
      removed = emptyList()
    )

    assertThat(isUpdated).isFalse()
    assertThat(dao.readingBookmarks().placed()).isEmpty()
  }

  /** mobile-sync's 15-line IndoPak page for [MADANI_PAGE] of the 1405 mushaf. */
  private suspend fun indoPakPageForMadaniPage(): Int {
    val indoPakPage = (repository.getReadingBookmarks(Mushaf.INDOPAK_15_LINES).single() as SyncPageReadingBookmark).page
    // pages differ between the two, so equal pages would mean nothing was mapped
    assertThat(indoPakPage).isNotEqualTo(MADANI_PAGE)
    return indoPakPage
  }

  /** Records the mushaf each rename reads its slot in, which nothing else observes. */
  private class RenameMushafSpy(
    private val repository: ReadingBookmarksRepository
  ) : ReadingBookmarksRepository by repository {
    val renameMushafs = mutableListOf<Mushaf>()

    override suspend fun renameReadingBookmark(
      slot: ReadingBookmarkSlot,
      name: String?,
      mushaf: Mushaf
    ): SyncReadingBookmark {
      renameMushafs += mushaf
      return repository.renameReadingBookmark(slot, name, mushaf)
    }

    override suspend fun renameReadingBookmark(
      slot: ReadingBookmarkSlot,
      name: String?,
      mushaf: Mushaf,
      timestamp: PlatformDateTime
    ): SyncReadingBookmark {
      renameMushafs += mushaf
      return repository.renameReadingBookmark(slot, name, mushaf, timestamp)
    }
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
    private val dataSource: QuranDataSource?
  ) : PageProvider {
    override fun getDataSource(): QuranDataSource =
      dataSource ?: throw UnsupportedOperationException("mobile-sync maps this page type's pages")

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

  /**
   * The pins that are actually pointing somewhere. Every slot that has ever been touched keeps a
   * row, so the dao's list is not empty just because nothing is placed.
   */
  private fun List<ReadingBookmark>.placed(): List<ReadingBookmark> =
    filterNot { it is EmptyReadingBookmark }

  private companion object {
    const val MADANI_PAGE = 300
    const val SHEMERLY_PAGE = 300
  }

}
