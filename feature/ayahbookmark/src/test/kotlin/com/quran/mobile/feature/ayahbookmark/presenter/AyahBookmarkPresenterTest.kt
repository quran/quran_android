package com.quran.mobile.feature.ayahbookmark.presenter

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.LastJumpLocation
import com.quran.data.core.QuranInfo
import com.quran.data.core.ReadingBookmarkSelector
import com.quran.data.core.SuggestedReadingBookmark
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.HighlightsDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.collection.ReadingCollection
import com.quran.data.model.collection.ReadingCollectionBookmarks
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import com.quran.data.source.QuranDataSource
import com.quran.mobile.feature.ayahbookmark.readingbookmark.presenter.ReadingBookmarkSheetPresenter
import com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkEvent
import com.quran.page.common.data.QuranNaming
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AyahBookmarkPresenterTest {

  private val ayah = SuraAyah(2, 255)
  private val appScope = AppCoroutineScope()
  private val bookmarksDao = FakeBookmarksDao()
  private val highlightsDao = FakeHighlightsDao()
  private val readingBookmarksDao = FakeReadingBookmarksDao()

  @After
  fun tearDown() {
    appScope.cancel()
  }

  private fun createPresenter(): AyahBookmarkPresenter {
    val quranInfo = QuranInfo(FakeQuranDataSource)
    val quranNaming = FakeQuranNaming()
    val suggested = SuggestedReadingBookmark(
      LastJumpLocation(),
      readingBookmarksDao,
      ReadingBookmarkSelector(quranInfo),
      appScope
    )
    val sheetFactory = ReadingBookmarkSheetPresenter.Factory { target, isNested, onAction ->
      ReadingBookmarkSheetPresenter(
        target = target,
        isNested = isNested,
        onAction = onAction,
        readingBookmarksDao = readingBookmarksDao,
        quranNaming = quranNaming,
        quranInfo = quranInfo,
        appCoroutineScope = appScope
      )
    }
    return AyahBookmarkPresenter(
      currentAyah = ayah,
      onReadingBookmarkAction = {},
      bookmarksDao = bookmarksDao,
      highlightsDao = highlightsDao,
      readingBookmarksDao = readingBookmarksDao,
      suggestedReadingBookmark = suggested,
      quranNaming = quranNaming,
      quranInfo = quranInfo,
      readingBookmarkSheetPresenterFactory = sheetFactory,
      appCoroutineScope = appScope
    )
  }

  private fun currentAppScopeJobs() = appScope.coroutineContext.job.children.toSet()

  private suspend fun awaitNewAppScopeJobs(before: Set<kotlinx.coroutines.Job>) {
    appScope.coroutineContext.job.children
      .filter { it !in before }
      .toList()
      .forEach { it.join() }
  }

  private suspend fun TurbineTestContext<com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkState>.awaitMatching(
    predicate: (com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkState) -> Boolean
  ): com.quran.mobile.feature.ayahbookmark.state.AyahBookmarkState {
    while (true) {
      val item = awaitItem()
      if (predicate(item)) return item
    }
  }

  @Test
  fun `tapping Done after async highlight load preserves existing highlight`() = runTest {
    val presenter = createPresenter()

    moleculeFlow(RecompositionMode.Immediate) {
      presenter.present()
    }.test {
      val initial = awaitItem()
      assertThat(initial.highlight).isNull()

      highlightsDao.emit(Highlight(ayah, HighlightColor.BLUE, Instant.fromEpochSeconds(100)))
      val loaded = awaitMatching { it.highlight?.color == HighlightColor.BLUE }

      val jobsBefore = currentAppScopeJobs()
      loaded.eventSink(AyahBookmarkEvent.Done)
      val dismissed = awaitMatching { it.isDismissed }
      assertThat(dismissed.isDismissed).isTrue()
      awaitNewAppScopeJobs(jobsBefore)

      assertThat(highlightsDao.clearedAyahs).isEmpty()
      assertThat(highlightsDao.setCalls).isEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `changing highlight color after async load updates state and persists new color on Done`() = runTest {
    val presenter = createPresenter()

    moleculeFlow(RecompositionMode.Immediate) {
      presenter.present()
    }.test {
      awaitItem()

      highlightsDao.emit(Highlight(ayah, HighlightColor.BLUE, Instant.fromEpochSeconds(100)))
      val loaded = awaitMatching { it.highlight?.color == HighlightColor.BLUE }

      loaded.eventSink(AyahBookmarkEvent.SetHighlight(HighlightColor.GREEN))
      val updated = awaitMatching { it.highlight?.color == HighlightColor.GREEN }

      val jobsBefore = currentAppScopeJobs()
      updated.eventSink(AyahBookmarkEvent.Done)
      val dismissed = awaitMatching { it.isDismissed }
      assertThat(dismissed.isDismissed).isTrue()
      awaitNewAppScopeJobs(jobsBefore)

      assertThat(highlightsDao.clearedAyahs).isEmpty()
      assertThat(highlightsDao.setCalls).containsExactly(ayah to HighlightColor.GREEN)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `clearing highlight after async load clears state and persists removal on Done`() = runTest {
    val presenter = createPresenter()

    moleculeFlow(RecompositionMode.Immediate) {
      presenter.present()
    }.test {
      awaitItem()

      highlightsDao.emit(Highlight(ayah, HighlightColor.BLUE, Instant.fromEpochSeconds(100)))
      val loaded = awaitMatching { it.highlight?.color == HighlightColor.BLUE }

      loaded.eventSink(AyahBookmarkEvent.ClearHighlight)
      val cleared = awaitMatching { it.highlight == null }

      val jobsBefore = currentAppScopeJobs()
      cleared.eventSink(AyahBookmarkEvent.Done)
      awaitMatching { it.isDismissed }
      awaitNewAppScopeJobs(jobsBefore)

      assertThat(highlightsDao.clearedAyahs).containsExactly(ayah)
      assertThat(highlightsDao.setCalls).isEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  private class FakeHighlightsDao : HighlightsDao {
    private val flow = MutableStateFlow<List<Highlight>>(emptyList())
    val setCalls = mutableListOf<Pair<SuraAyah, HighlightColor>>()
    val clearedAyahs = mutableListOf<SuraAyah>()

    fun emit(highlight: Highlight) {
      flow.value = listOf(highlight)
    }

    override fun highlightsFlow(): Flow<List<Highlight>> = flow

    override fun highlightsFlow(currentAyah: SuraAyah): Flow<Highlight?> =
      flow.map { list -> list.firstOrNull { it.suraAyah == currentAyah } }

    override suspend fun setHighlight(ayah: SuraAyah, color: HighlightColor) {
      setCalls += ayah to color
    }

    override suspend fun clearHighlight(ayah: SuraAyah) {
      clearedAyahs += ayah
    }
  }

  private class FakeBookmarksDao : BookmarksDao {
    override val changes: Flow<Unit> = MutableSharedFlow()
    private val collections = MutableStateFlow<List<ReadingCollectionBookmarks>>(emptyList())

    override suspend fun bookmarks(sortOrder: Int): List<Bookmark> = emptyList()
    override fun bookmarksFlow(sortOrder: Int): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
    override fun bookmarksForPage(page: Int): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
    override fun collectionsWithBookmarksFlow(): Flow<List<ReadingCollectionBookmarks>> = collections
    override suspend fun addCollection(name: String): ReadingCollection =
      ReadingCollection("id", name, Instant.DISTANT_PAST, false)
    override suspend fun tags(): List<Tag> = emptyList()
    override fun tagsFlow(): Flow<List<Tag>> = MutableStateFlow(emptyList())
    override suspend fun addTag(name: String): String = "id"
    override suspend fun updateTag(tag: Tag): Boolean = true
    override suspend fun removeTags(tags: List<Tag>) = Unit
    override suspend fun getBookmarkTagIds(bookmarkId: String): List<String> = emptyList()
    override suspend fun updateBookmarkTags(
      bookmarkIds: Array<String>,
      tagIds: Set<String>,
      deleteNonTagged: Boolean
    ): Boolean = true
    override suspend fun removeBookmarkFromTag(bookmark: Bookmark, tagId: String): Boolean = true
    override suspend fun removeBookmarks(bookmarks: List<Bookmark>) = Unit
    override suspend fun deleteAyahBookmark(suraAyah: SuraAyah): Boolean = true
    override suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean = false
    override suspend fun replaceAyahBookmarkCollections(
      suraAyah: SuraAyah,
      collectionIds: Set<String>
    ): Boolean = true
  }

  private class FakeReadingBookmarksDao : ReadingBookmarksDao {
    private val flow = MutableStateFlow<List<ReadingBookmark>>(emptyList())
    override fun readingBookmarksFlow(): Flow<List<ReadingBookmark>> = flow
    override suspend fun readingBookmarks(): List<ReadingBookmark> = flow.value
    override suspend fun setPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean = true
    override suspend fun setAyahReadingBookmark(slot: ReadingBookmarkType, suraAyah: SuraAyah): Boolean = true
    override suspend fun clearReadingBookmark(slot: ReadingBookmarkType): ReadingBookmark =
      error("Unused")
    override suspend fun renameReadingBookmark(slot: ReadingBookmarkType, name: String?) = Unit
    override suspend fun isPageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean = false
    override suspend fun togglePageReadingBookmark(slot: ReadingBookmarkType, page: Int): Boolean = true
    override suspend fun updateReadingBookmarks(
      ayah: SuraAyah,
      added: List<ReadingBookmark>,
      removed: List<ReadingBookmark>
    ): Boolean = true
  }

  private class FakeQuranNaming : QuranNaming {
    override fun getSuraName(context: android.content.Context, sura: Int, wantPrefix: Boolean): String =
      "Sura $sura"
    override fun getSuraNameWithNumber(context: android.content.Context, sura: Int, wantPrefix: Boolean): String =
      "$sura"
    override fun getSuraAyahString(context: android.content.Context, sura: Int, ayah: Int): String =
      "$sura:$ayah"
    override fun getSuraPageString(context: android.content.Context, page: Int): String =
      "Page $page"
  }

  private object FakeQuranDataSource : QuranDataSource {
    override val numberOfPages: Int = 604
    override val pageForSuraArray: IntArray = IntArray(114) { 1 }
    override val suraForPageArray: IntArray = IntArray(604) { 1 }
    override val ayahForPageArray: IntArray = IntArray(604) { 1 }
    override val pageForJuzArray: IntArray = IntArray(30) { 1 }
    override val juzDisplayPageArrayOverride: Map<Int, Int> = emptyMap()
    override val numberOfAyahsForSuraArray: IntArray = IntArray(114) { 286 }
    override val isMakkiBySuraArray: BooleanArray = BooleanArray(114)
    override val quarterStartByPage: IntArray = IntArray(604) { 1 }
    override val quartersArray: Array<SuraAyah> = Array(240) { SuraAyah(1, 1) }
    override val manzilPageArray: Array<Int> = Array(7) { 1 }
    override val haveSidelines: Boolean = false
    override val pagesToSkip: Int = 0
  }
}
