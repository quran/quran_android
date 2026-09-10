package com.quran.labs.androidquran.presenter.bookmark

import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.HighlightsDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.highlight.Highlight
import com.quran.labs.androidquran.dao.bookmark.BookmarkListMode
import com.quran.labs.androidquran.dao.bookmark.BookmarkListRowData
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class BookmarkListPresenter @Inject constructor(
  private val bookmarksDao: BookmarksDao,
  private val highlightsDao: HighlightsDao,
  private val quranInfo: QuranInfo,
  private val arabicDatabaseUtils: Provider<ArabicDatabaseUtils>,
  private val appCoroutineScope: AppCoroutineScope
) {
  private val pendingRemovalKeys = MutableStateFlow<Set<String>>(emptySet())
  private var pendingRemovals: List<BookmarkListRowData> = emptyList()
  private var removalJob: Job? = null

  fun rows(mode: BookmarkListMode, sortOrder: Int): Flow<List<BookmarkListRowData>> {
    return when (mode) {
      is BookmarkListMode.Collection -> collectionRows(mode.collectionId, sortOrder)
      is BookmarkListMode.Highlights -> highlightRows(mode, sortOrder)
    }
  }

  fun collectionName(collectionId: String): Flow<String?> {
    return bookmarksDao.tagsFlow()
      .map { tags -> tags.firstOrNull { tag -> tag.id == collectionId }?.name }
  }

  private fun collectionRows(
    collectionId: String,
    sortOrder: Int
  ): Flow<List<BookmarkListRowData>> {
    return combine(
      bookmarksDao.bookmarksFlow(sortOrder),
      pendingRemovalKeys
    ) { bookmarks, pending ->
      bookmarks
        .filterNot { bookmark -> bookmark.isPageBookmark() }
        .filter { bookmark -> collectionId in bookmark.tags }
        .filterNot { bookmark -> bookmark.id in pending }
    }
      .map { bookmarks ->
        hydrateAyahText(bookmarks).map { bookmark ->
          BookmarkListRowData.BookmarkItem(bookmark, collectionId)
        }
      }
      .flowOn(Dispatchers.IO)
  }

  private fun highlightRows(
    mode: BookmarkListMode.Highlights,
    sortOrder: Int
  ): Flow<List<BookmarkListRowData>> {
    return combine(
      highlightsDao.highlightsFlow(),
      pendingRemovalKeys
    ) { highlights, pending ->
      highlights
        .filter { highlight -> highlight.color == mode.color }
        .filterNot { highlight -> highlightKey(highlight.suraAyah) in pending }
    }
      .map { highlights -> highlightRowData(highlights, sortOrder) }
      .flowOn(Dispatchers.IO)
  }

  private suspend fun highlightRowData(
    highlights: List<Highlight>,
    sortOrder: Int
  ): List<BookmarkListRowData> {
    val ayahText = ayahTextFor(highlights)

    if (sortOrder == BookmarkSortOrder.SORT_DATE_ADDED) {
      return highlights
        .sortedByDescending { highlight -> highlight.timestamp }
        .map { highlight ->
          BookmarkListRowData.HighlightItem(highlight, ayahText[highlight.suraAyah])
        }
    }

    val sorted = highlights.sortedWith(
      compareBy({ it.suraAyah.sura }, { it.suraAyah.ayah })
    )
    return buildList {
      var currentSura = -1
      sorted.forEach { highlight ->
        if (highlight.suraAyah.sura != currentSura) {
          currentSura = highlight.suraAyah.sura
          add(BookmarkListRowData.SuraHeader(currentSura))
        }
        add(BookmarkListRowData.HighlightItem(highlight, ayahText[highlight.suraAyah]))
      }
    }
  }

  private suspend fun ayahTextFor(highlights: List<Highlight>): Map<SuraAyah, String> {
    if (highlights.isEmpty()) {
      return emptyMap()
    }

    return withContext(Dispatchers.IO) {
      try {
        val ayahIds = highlights.associateBy { highlight ->
          quranInfo.getAyahId(highlight.suraAyah.sura, highlight.suraAyah.ayah)
        }
        arabicDatabaseUtils().getAyahTextForAyat(ayahIds.keys.toList())
          .mapNotNull { (ayahId, text) -> ayahIds[ayahId]?.suraAyah?.let { it to text } }
          .toMap()
      } catch (throwable: Throwable) {
        // the arabic database is optional - fall back to showing sura and ayah names
        Timber.d(throwable, "Unable to hydrate highlight ayah text")
        emptyMap()
      }
    }
  }

  private fun hydrateAyahText(bookmarks: List<Bookmark>): List<Bookmark> {
    return try {
      arabicDatabaseUtils().hydrateAyahText(bookmarks.toMutableList())
    } catch (throwable: Throwable) {
      Timber.d(throwable, "Unable to hydrate bookmark ayah text")
      bookmarks
    }
  }

  fun hasPendingRemovals(): Boolean = pendingRemovals.isNotEmpty()

  fun removeAfterSomeTime(rows: List<BookmarkListRowData>, delayInMs: Long) {
    if (rows.isNotEmpty()) {
      removalJob?.cancel()
      pendingRemovals = pendingRemovals + rows
      pendingRemovalKeys.value = pendingRemovals.mapNotNull(::removalKey).toSet()

      removalJob = appCoroutineScope.launch {
        delay(delayInMs.milliseconds)
        commit(pendingRemovals)
      }
    }
  }

  fun cancelRemoval() {
    removalJob?.cancel()
    removalJob = null
    pendingRemovals = emptyList()
    pendingRemovalKeys.value = emptySet()
  }

  fun flushPendingRemovals() {
    val toRemove = pendingRemovals
    if (toRemove.isNotEmpty()) {
      removalJob?.cancel()
      removalJob = null
      appCoroutineScope.launch { commit(toRemove) }
    }
  }

  private suspend fun commit(toRemove: List<BookmarkListRowData>) {
    withContext(NonCancellable) {
      try {
        toRemove.forEach { row ->
          when (row) {
            is BookmarkListRowData.BookmarkItem ->
              bookmarksDao.removeBookmarkFromTag(row.bookmark, row.collectionId)

            is BookmarkListRowData.HighlightItem ->
              highlightsDao.clearHighlight(row.highlight.suraAyah)

            is BookmarkListRowData.SuraHeader -> Unit
          }
        }
      } catch (throwable: Throwable) {
        Timber.e(throwable, "Failed to remove bookmark list items")
      }
    }

    pendingRemovals = emptyList()
    pendingRemovalKeys.value = emptySet()
  }

  private fun removalKey(row: BookmarkListRowData): String? {
    return when (row) {
      is BookmarkListRowData.BookmarkItem -> row.bookmark.id
      is BookmarkListRowData.HighlightItem -> highlightKey(row.highlight.suraAyah)
      is BookmarkListRowData.SuraHeader -> null
    }
  }

  private fun highlightKey(suraAyah: SuraAyah): String = "${suraAyah.sura}:${suraAyah.ayah}"
}
