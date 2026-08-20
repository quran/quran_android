package com.quran.labs.androidquran.presenter.bookmark

import androidx.annotation.VisibleForTesting
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.quran.data.dao.BookmarkSortOrder
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.HighlightsDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.RecentPagesDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.common.ui.core.HighlightColors
import com.quran.labs.androidquran.dao.bookmark.AyahMark
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.BookmarkItem
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.HighlightColorItem
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.HighlightedAyahItem
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.HighlightsHeader
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.ReadingBookmarkHeader
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.ReadingBookmarkItem
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.RecentPageHeader
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.TagHeader
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.observers.DisposableSingleObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

open class BookmarkPresenter @Inject internal constructor(
  private val bookmarksDao: BookmarksDao,
  private val recentPagesDao: RecentPagesDao,
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val highlightsDao: HighlightsDao,
  private val quranSettings: QuranSettings,
) : Presenter<BookmarksFragment> {
  private var sortOrder: Int = quranSettings.bookmarksSortOrder
  var isGroupedByTags: Boolean = quranSettings.bookmarksGroupedByTags
    private set
  var isShowingRecents: Boolean = quranSettings.showRecents
    private set
  var isDateShowing: Boolean = quranSettings.showDate
    private set

  private var collapsedCollections: Set<String> = quranSettings.collapsedCollections

  private var cachedData: BookmarkRawResult? = null
  private var fragment: BookmarksFragment? = null

  private var pendingRemoval: DisposableSingleObserver<BookmarkRawResult>? = null
  private var itemsToRemove: MutableList<QuranRow>? = null
  private val presenterScope = MainScope()

  init {
    subscribeToChanges()
  }

  open fun subscribeToChanges() {
    presenterScope.launch {
      try {
        bookmarksDao.changes.collect {
          onObservedDataChanged()
        }
      } catch (throwable: Throwable) {
        Timber.e(throwable, "Error observing bookmark changes")
      }
    }

    presenterScope.launch {
      try {
        recentPagesDao.recentPagesFlow()
          .drop(1)
          .collect {
            onObservedDataChanged()
          }
      } catch (throwable: Throwable) {
        Timber.e(throwable, "Error observing recent page changes")
      }
    }

    presenterScope.launch {
      try {
        readingBookmarksDao.readingBookmarkFlow()
          .drop(1)
          .collect {
            onObservedDataChanged()
          }
      } catch (throwable: Throwable) {
        Timber.e(throwable, "Error observing reading bookmark changes")
      }
    }

    presenterScope.launch {
      try {
        highlightsDao.highlightsFlow()
          .drop(1)
          .collect {
            onObservedDataChanged()
          }
      } catch (throwable: Throwable) {
        Timber.e(throwable, "Error observing highlight changes")
      }
    }
  }

  private fun onObservedDataChanged() {
    if (fragment != null) {
      requestData(false)
    } else {
      cachedData = null
    }
  }

  fun getSortOrder(): Int = sortOrder

  fun setSortOrder(sortOrder: Int) {
    this.sortOrder = sortOrder
    quranSettings.bookmarksSortOrder = sortOrder
    requestData(false)
  }

  fun toggleGroupByTags() {
    isGroupedByTags = !isGroupedByTags
    quranSettings.bookmarksGroupedByTags = isGroupedByTags
    requestData(false)
  }

  fun toggleShowRecents() {
    isShowingRecents = !isShowingRecents
    quranSettings.showRecents = isShowingRecents
    requestData(false)
  }

  fun toggleShowDate() {
    isDateShowing = !isDateShowing
    quranSettings.showDate = isDateShowing
    requestData(false)
  }

  fun toggleCollectionCollapsed(collectionId: String) {
    collapsedCollections = if (collectionId in collapsedCollections) {
      collapsedCollections - collectionId
    } else {
      collapsedCollections + collectionId
    }
    quranSettings.collapsedCollections = collapsedCollections
    requestData(false)
  }

  fun shouldShowInlineTags(): Boolean = !isGroupedByTags

  fun getContextualOperationsForItems(rows: List<QuranRow>): BooleanArray {
    val headers = rows.count { row -> row.isEditableCollectionHeader }
    val bookmarks = rows.count { row -> row.isBookmark }
    return booleanArrayOf(
      headers == 1 && bookmarks == 0,
      (headers + bookmarks) > 0,
      headers == 0 && bookmarks > 0
    )
  }

  fun requestData(canCache: Boolean) {
    val cachedData = cachedData
    if (canCache && cachedData != null) {
      fragment?.let {
        Timber.d("sending cached bookmark data")
        it.onNewRawData(cachedData)
      }
      return
    }

    Timber.d("requesting bookmark data from the database")
    getBookmarks(sortOrder, isGroupedByTags)
  }

  fun deleteAfterSomeTime(selectedRows: List<QuranRow>) {
    if (selectedRows.isEmpty()) {
      return
    }
    val fragment = fragment ?: return

    val mergedItems = selectedRows.toMutableList().apply {
      itemsToRemove?.let { addAll(it) }
    }

    predictQuranListAfterDeletion(mergedItems)?.let(fragment::onNewRawData)

    if (pendingRemoval != null) {
      cancelDeletion()
    }

    itemsToRemove = mergedItems
    pendingRemoval = Single.timer(
      DELAY_DELETION_DURATION_IN_MS.toLong(),
      TimeUnit.MILLISECONDS
    )
      .flatMap { removeItemsObservable() }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeWith(object : DisposableSingleObserver<BookmarkRawResult>() {
        override fun onSuccess(result: BookmarkRawResult) {
          pendingRemoval = null
          itemsToRemove = null
          cachedData = result
          this@BookmarkPresenter.fragment?.onNewRawData(result)
        }

        override fun onError(e: Throwable) {
          Timber.e(e, "Failed to remove bookmarks")
        }
      })
  }

  private fun predictQuranListAfterDeletion(remove: List<QuranRow>): BookmarkRawResult? {
    val currentData = cachedData ?: return null
    return previewAfterDeletion(currentData, remove)
  }

  @VisibleForTesting
  fun previewAfterDeletion(
    currentData: BookmarkRawResult,
    remove: List<QuranRow>
  ): BookmarkRawResult {
    val cachedRows = currentData.rows

    val bookmarkIdsToRemove = mutableSetOf<String>()
    val tagIdsToUntag = mutableSetOf<String>()
    val bookmarkTagContext = mutableMapOf<String, MutableSet<String>>()

    for (row in remove) {
      val bookmarkId = row.bookmarkId
      val tagId = row.tagId
      when {
        row.isBookmark && bookmarkId != null -> {
          if (isGroupedByTags && tagId != null) {
            val contextTags = bookmarkTagContext[bookmarkId] ?: mutableSetOf<String>().also {
              bookmarkTagContext[bookmarkId] = it
            }
            contextTags.add(tagId)
          } else {
            bookmarkIdsToRemove.add(bookmarkId)
          }
        }

        row.isBookmarkHeader && tagId != null -> tagIdsToUntag.add(tagId)
      }
    }

    val filteredRows = mutableListOf<BookmarkRowData>()
    val removedCountByCollection = mutableMapOf<String?, Int>()

    for (rowData in cachedRows) {
      when (rowData) {
        is BookmarkItem -> {
          val bookmarkId = rowData.bookmark.id
          val currentTagId = rowData.tagId
          var shouldKeep = true

          if (bookmarkIdsToRemove.contains(bookmarkId)) {
            shouldKeep = false
          } else if (bookmarkTagContext.containsKey(bookmarkId)) {
            val contextTagIds = bookmarkTagContext[bookmarkId]
            if (contextTagIds != null && currentTagId != null && contextTagIds.contains(currentTagId)) {
              shouldKeep = false
            } else if (currentTagId != null && tagIdsToUntag.contains(currentTagId)) {
              shouldKeep = false
            }
          } else if (currentTagId != null && tagIdsToUntag.contains(currentTagId)) {
            shouldKeep = false
          }

          if (shouldKeep) {
            filteredRows += rowData
          } else {
            removedCountByCollection[currentTagId] =
              (removedCountByCollection[currentTagId] ?: 0) + 1
          }
        }

        is TagHeader -> {
          val tagId = rowData.tag.id
          if (!tagIdsToUntag.contains(tagId)) {
            filteredRows += rowData
          }
        }

        else -> filteredRows += rowData
      }
    }

    val previewRows = filteredRows.map { rowData ->
      when (rowData) {
        is TagHeader -> rowData.withCountDelta(-(removedCountByCollection[rowData.tag.id] ?: 0))

        else -> rowData
      }
    }

    val filteredTagMap = currentData.tagMap.toMutableMap().apply {
      tagIdsToUntag.forEach { remove(it) }
    }

    return BookmarkRawResult(previewRows, filteredTagMap)
  }

  private fun removeItemsObservable(): Single<BookmarkRawResult> {
    val items = itemsToRemove?.toList()
      ?: return Single.error(IllegalStateException("No pending items to remove"))

    return Single.fromCallable {
      runBlocking {
        removeItems(items)
        getBookmarksList(sortOrder, isGroupedByTags)
      }
    }
      .subscribeOn(Schedulers.io())
  }

  fun cancelDeletion() {
    pendingRemoval?.dispose()
    pendingRemoval = null
    itemsToRemove = null
  }

  private suspend fun removeItems(items: List<QuranRow>) {
    withContext(Dispatchers.IO) {
      val tagsToDelete = mutableListOf<Tag>()
      val bookmarksToDelete = mutableListOf<Bookmark>()
      val bookmarksToUntag = mutableListOf<Pair<Bookmark, String>>()

      items.forEach { row ->
        val tagId = row.tagId
        when {
          row.isBookmarkHeader && tagId != null -> {
            tagsToDelete += Tag(tagId, row.text)
          }

          row.isBookmark && row.bookmark != null -> {
            if (tagId != null) {
              bookmarksToUntag += row.bookmark to tagId
            } else {
              bookmarksToDelete += row.bookmark
            }
          }
        }
      }

      bookmarksDao.removeTags(tagsToDelete)
      bookmarksToUntag.forEach { (bookmark, tagId) ->
        bookmarksDao.removeBookmarkFromTag(bookmark, tagId)
      }
      bookmarksDao.removeBookmarks(bookmarksToDelete)
    }
  }

  private suspend fun getBookmarkData(sortOrder: Int): BookmarkData {
    return withContext(Dispatchers.IO) {
      BookmarkData(
        tags = bookmarksDao.tags(),
        bookmarks = bookmarksDao.bookmarks(sortOrder)
      )
    }
  }

  private suspend fun getBookmarksWithRecentPages(sortOrder: Int): BookmarkData {
    return coroutineScope {
      val bookmarkData = async { getBookmarkData(sortOrder) }
      val recentPages = async { getRecentPages() }

      bookmarkData.await().copy(recentPages = recentPages.await())
    }
  }

  private suspend fun getRecentPages(): List<RecentPage> {
    return withContext(Dispatchers.IO) { recentPagesDao.recentPages() }
  }

  @VisibleForTesting
  suspend fun getBookmarksList(sortOrder: Int, groupByTags: Boolean): BookmarkRawResult {
    return coroutineScope {
      val bookmarkData = async { getBookmarksWithRecentPages(sortOrder) }
      val readingBookmark = async { readingBookmarksDao.readingBookmark() }
      val highlights = async { highlightsDao.highlightsFlow().first() }
      val data = bookmarkData.await()
      val rows = getBookmarkRowData(
        data, sortOrder, groupByTags, readingBookmark.await(), highlights.await()
      )
      val tagMap = generateTagMap(data.tags)
      BookmarkRawResult(rows, tagMap)
    }
  }

  private fun getBookmarks(sortOrder: Int, groupByTags: Boolean) {
    presenterScope.launch {
      try {
        val result = getBookmarksList(sortOrder, groupByTags)
        cachedData = result
        val fragment = fragment
        if (fragment != null) {
          val itemsPendingRemoval = itemsToRemove
          if (pendingRemoval != null && !itemsPendingRemoval.isNullOrEmpty()) {
            val preview = predictQuranListAfterDeletion(itemsPendingRemoval) ?: result
            fragment.onNewRawData(preview)
          } else {
            fragment.onNewRawData(result)
          }
        }
      } catch (throwable: Throwable) {
        Timber.e(throwable, "Unable to load bookmarks")
      }
    }
  }

  private fun getBookmarkRowData(
    data: BookmarkData,
    sortOrder: Int,
    groupByTags: Boolean,
    readingBookmark: ReadingBookmark?,
    highlights: List<Highlight>
  ): MutableList<BookmarkRowData> {
    val rows = mutableListOf<BookmarkRowData>()

    if (readingBookmark != null) {
      rows.add(ReadingBookmarkHeader)
      rows.add(ReadingBookmarkItem(readingBookmark))
    }

    val recentPages = data.recentPages
    if (recentPages.isNotEmpty()) {
      val size = if (isShowingRecents) recentPages.size else 1
      rows.add(RecentPageHeader(size))
      for (i in 0 until size) {
        rows.add(BookmarkRowData.RecentPage(recentPages[i]))
      }
    }

    rows.addAll(getHighlightRowData(highlights))

    rows.addAll(
      if (groupByTags) {
        getRowDataSortedByTags(data.tags, data.bookmarks)
      } else {
        getSortedRowData(data.bookmarks, highlights, sortOrder)
      }
    )
    return rows
  }

  private fun getHighlightRowData(highlights: List<Highlight>): List<BookmarkRowData> {
    return if (highlights.isEmpty()) {
      emptyList()
    } else {
      val countsByColor: Map<HighlightColor, Int> =
        highlights.groupingBy { highlight -> highlight.color }.eachCount()
      buildList {
        add(HighlightsHeader)
        HighlightColors.sorted.forEach { spec ->
          add(HighlightColorItem(spec.highlightColor, countsByColor[spec.highlightColor] ?: 0))
        }
      }
    }
  }

  private fun getRowDataSortedByTags(
    tags: List<Tag>,
    bookmarks: List<Bookmark>
  ): MutableList<BookmarkRowData> {
    val rows = mutableListOf<BookmarkRowData>()
    val ayahBookmarks = bookmarks.filterNot { bookmark -> bookmark.isPageBookmark() }
    val bookmarksByTagId = bookmarksByCollection(tags, ayahBookmarks)

    val (systemCollections, userCollections) = tags.partition { tag -> tag.isSystem }
    for (tag in systemCollections + userCollections) {
      val tagBookmarks = bookmarksByTagId[tag.id].orEmpty()
      val isCollapsed = tag.id in collapsedCollections
      rows.add(TagHeader(tag, tagBookmarks.size, isCollapsed))
      if (!isCollapsed) {
        for (bookmark in tagBookmarks) {
          rows.add(BookmarkItem(bookmark, tag.id))
        }
      }
    }
    return rows
  }

  private fun getSortedRowData(
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    sortOrder: Int
  ): MutableList<BookmarkRowData> {
    val highlightsByAyah = highlights.associateBy { highlight -> highlight.suraAyah }
    val ayahBookmarks = bookmarks.filterNot { bookmark -> bookmark.isPageBookmark() }
    val bookmarkedAyat = ayahBookmarks.mapTo(mutableSetOf()) { bookmark -> bookmark.suraAyah() }
    val unbookmarkedHighlights = highlightsByAyah.values
      .filterNot { highlight -> highlight.suraAyah in bookmarkedAyat }

    return if (ayahBookmarks.isEmpty() && unbookmarkedHighlights.isEmpty()) {
      mutableListOf()
    } else {
      val entries = ayahBookmarks.map { bookmark ->
        AyahEntry(
          row = BookmarkItem(bookmark, null, highlightsByAyah.markFor(bookmark)),
          suraAyah = bookmark.suraAyah(),
          timestamp = bookmark.timestamp
        )
      } + unbookmarkedHighlights.map { highlight ->
        AyahEntry(
          row = HighlightedAyahItem(highlight),
          suraAyah = highlight.suraAyah,
          timestamp = highlight.timestamp.epochSeconds
        )
      }

      val sorted = if (sortOrder == BookmarkSortOrder.SORT_LOCATION) {
        entries.sortedBy { entry -> entry.suraAyah }
      } else {
        entries.sortedByDescending { entry -> entry.timestamp }
      }

      val rows = mutableListOf<BookmarkRowData>(BookmarkRowData.AyahBookmarksHeader)
      sorted.mapTo(rows) { entry -> entry.row }
      rows
    }
  }

  private fun Bookmark.suraAyah(): SuraAyah = SuraAyah(sura!!, ayah!!)

  private fun Map<SuraAyah, Highlight>.markFor(bookmark: Bookmark): AyahMark {
    val highlight = this[bookmark.suraAyah()]
    return if (highlight == null) AyahMark.Unhighlighted else AyahMark.Highlighted(highlight.color)
  }

  private class AyahEntry(
    val row: BookmarkRowData,
    val suraAyah: SuraAyah,
    val timestamp: Long
  )

  private fun bookmarksByCollection(
    tags: List<Tag>,
    bookmarks: List<Bookmark>
  ): Map<String, List<Bookmark>> {
    return tags.associate { tag ->
      tag.id to bookmarks.filter { bookmark -> bookmark.tags.contains(tag.id) }
    }
  }

  private fun generateTagMap(tags: List<Tag>): Map<String, Tag> {
    return tags.associateByTo(mutableMapOf()) { it.id }
  }

  override fun bind(fragment: BookmarksFragment) {
    this.fragment = fragment
    requestData(true)
  }

  override fun unbind(fragment: BookmarksFragment) {
    if (fragment == this.fragment) {
      this.fragment = null
    }
  }

  companion object {
    @BaseTransientBottomBar.Duration
    const val DELAY_DELETION_DURATION_IN_MS: Int = 4 * 1000 // 4 seconds
  }

}
