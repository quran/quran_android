package com.quran.labs.androidquran.presenter.bookmark

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.BookmarkItem
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.NotTaggedHeader
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.RecentPageHeader
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.TagHeader
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.observers.DisposableSingleObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit

open class BookmarkPresenter @Inject internal constructor(
  private val bookmarkModel: BookmarkModel,
  private val quranSettings: QuranSettings,
  private val arabicDatabaseUtils: Provider<ArabicDatabaseUtils>,
) : Presenter<BookmarksFragment> {
  private var sortOrder: Int = quranSettings.bookmarksSortOrder
  var isGroupedByTags: Boolean = quranSettings.bookmarksGroupedByTags
    private set
  var isShowingRecents: Boolean = quranSettings.showRecents
    private set
  var isDateShowing: Boolean = quranSettings.showDate
    private set

  private var cachedData: BookmarkRawResult? = null
  private var fragment: BookmarksFragment? = null

  private var pendingRemoval: DisposableSingleObserver<BookmarkRawResult>? = null
  private var itemsToRemove: MutableList<QuranRow>? = null

  init {
    subscribeToChanges()
  }

  @SuppressLint("CheckResult")
  open fun subscribeToChanges() {
    Observable.merge(
      bookmarkModel.tagsObservable(),
      bookmarkModel.bookmarksObservable(),
      bookmarkModel.recentPagesUpdatedObservable()
    )
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({ _ ->
        if (fragment != null) {
          requestData(false)
        } else {
          cachedData = null
        }
      }, { throwable ->
        Timber.e(throwable, "Error observing bookmark changes")
      })
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

  fun shouldShowInlineTags(): Boolean = !isGroupedByTags

  fun getContextualOperationsForItems(rows: List<QuranRow>): BooleanArray {
    val headers = rows.count { row -> row.isBookmarkHeader }
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
    val cachedRows = currentData.rows

    val bookmarkIdsToRemove = mutableSetOf<Long>()
    val tagIdsToUntag = mutableSetOf<Long>()
    val bookmarkTagContext = mutableMapOf<Long, MutableSet<Long>>()

    for (row in remove) {
      when {
        row.isBookmark && row.bookmarkId > 0 -> {
          if (isGroupedByTags && row.tagId > 0) {
            val contextTags = bookmarkTagContext[row.bookmarkId] ?: mutableSetOf<Long>().also {
              bookmarkTagContext[row.bookmarkId] = it
            }
            contextTags.add(row.tagId)
          } else {
            bookmarkIdsToRemove.add(row.bookmarkId)
          }
        }

        row.isBookmarkHeader && row.tagId > 0 -> tagIdsToUntag.add(row.tagId)
      }
    }

    val filteredRows = mutableListOf<BookmarkRowData>()
    val removedBookmarks = mutableSetOf<Bookmark>()
    var haveUntaggedSection = false

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
            removedBookmarks += rowData.bookmark
          }
        }

        is TagHeader -> {
          val tagId = rowData.tag.id
          if (!tagIdsToUntag.contains(tagId)) {
            filteredRows += rowData
          }
        }

        NotTaggedHeader -> {
          haveUntaggedSection = true
          filteredRows += rowData
        }

        else -> filteredRows += rowData
      }
    }

    val newlyUntaggedBookmarks = mutableSetOf<Bookmark>()
    for (removedBookmark in removedBookmarks) {
      val tags = removedBookmark.tags.toSet()
      if (tags.isNotEmpty()) {
        val tagsExplicitlyRemoved = bookmarkTagContext[removedBookmark.id].orEmpty()
        val tagsDeletedViaHeader = tagIdsToUntag.filterTo(mutableSetOf()) { it in tags }
        val tagsDeletedFromBookmark = tagsExplicitlyRemoved + tagsDeletedViaHeader
        if (tagsDeletedFromBookmark.containsAll(tags)) {
          newlyUntaggedBookmarks += removedBookmark
        }
      }
    }

    if (newlyUntaggedBookmarks.isNotEmpty()) {
      if (!haveUntaggedSection) {
        filteredRows += NotTaggedHeader
      }
      val cachedBookmarkItems = cachedRows.filterIsInstance<BookmarkItem>()
      for (bookmark in newlyUntaggedBookmarks) {
        val template = cachedBookmarkItems.firstOrNull { it.bookmark == bookmark }
        if (template != null) {
          filteredRows += BookmarkItem(template.bookmark, null)
        }
      }
    }

    val filteredTagMap = currentData.tagMap.toMutableMap().apply {
      tagIdsToUntag.forEach { remove(it) }
    }

    return BookmarkRawResult(filteredRows, filteredTagMap)
  }

  private fun removeItemsObservable(): Single<BookmarkRawResult> {
    val items = itemsToRemove?.toList()
      ?: return Single.error(IllegalStateException("No pending items to remove"))
    return bookmarkModel.removeItemsObservable(items)
      .andThen(getBookmarksListObservable(sortOrder, isGroupedByTags))
  }

  fun cancelDeletion() {
    pendingRemoval?.dispose()
    pendingRemoval = null
    itemsToRemove = null
  }

  private fun getBookmarksWithAyatObservable(sortOrder: Int): Single<BookmarkData> {
    return bookmarkModel.getBookmarkDataObservable(sortOrder)
      .map { bookmarkData ->
        try {
          bookmarkData.copy(
            bookmarks = arabicDatabaseUtils().hydrateAyahText(bookmarkData.bookmarks.toMutableList())
          )
        } catch (exception: Exception) {
          bookmarkData
        }
      }
  }

  @VisibleForTesting
  fun getBookmarksListObservable(sortOrder: Int, groupByTags: Boolean): Single<BookmarkRawResult> {
    return getBookmarksWithAyatObservable(sortOrder)
      .map { bookmarkData ->
        val rows = getBookmarkRowData(bookmarkData, groupByTags)
        val tagMap = generateTagMap(bookmarkData.tags)
        BookmarkRawResult(rows, tagMap)
      }
      .subscribeOn(Schedulers.io())
  }

  @SuppressLint("CheckResult")
  private fun getBookmarks(sortOrder: Int, groupByTags: Boolean) {
    getBookmarksListObservable(sortOrder, groupByTags)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({ result ->
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
      }, { throwable ->
        Timber.e(throwable, "Unable to load bookmarks")
      })
  }

  private fun getBookmarkRowData(
    data: BookmarkData,
    groupByTags: Boolean
  ): MutableList<BookmarkRowData> {
    val rows = if (groupByTags) {
      getRowDataSortedByTags(data.tags, data.bookmarks)
    } else {
      getSortedRowData(data.bookmarks)
    }

    val recentPages = data.recentPages
    var size = recentPages.size
    if (size > 0) {
      if (!isShowingRecents) {
        size = 1
      }
      rows.add(0, RecentPageHeader(size))
      for (i in 0 until size) {
        rows.add(i + 1, BookmarkRowData.RecentPage(recentPages[i]))
      }
    }
    return rows
  }

  private fun getRowDataSortedByTags(
    tags: List<Tag>,
    bookmarks: List<Bookmark>
  ): MutableList<BookmarkRowData> {
    val rows = mutableListOf<BookmarkRowData>()
    val tagsMapping = generateTagsMapping(tags, bookmarks)

    for (tag in tags) {
      rows.add(TagHeader(tag))
      val tagBookmarks = tagsMapping[tag.id].orEmpty()
      for (bookmark in tagBookmarks) {
        rows.add(BookmarkItem(bookmark, tag.id))
      }
    }

    val untagged = tagsMapping[BOOKMARKS_WITHOUT_TAGS_ID].orEmpty()
    if (untagged.isNotEmpty()) {
      rows.add(NotTaggedHeader)
      for (bookmark in untagged) {
        rows.add(BookmarkItem(bookmark, null))
      }
    }
    return rows
  }

  private fun getSortedRowData(bookmarks: List<Bookmark>): MutableList<BookmarkRowData> {
    val rows = mutableListOf<BookmarkRowData>()
    val ayahBookmarks = mutableListOf<Bookmark>()

    for (bookmark in bookmarks) {
      if (bookmark.isPageBookmark()) {
        rows.add(BookmarkItem(bookmark, null))
      } else {
        ayahBookmarks.add(bookmark)
      }
    }

    if (rows.isNotEmpty()) {
      rows.add(0, BookmarkRowData.PageBookmarksHeader)
    }

    if (ayahBookmarks.isNotEmpty()) {
      rows.add(BookmarkRowData.AyahBookmarksHeader)
      for (bookmark in ayahBookmarks) {
        rows.add(BookmarkItem(bookmark, null))
      }
    }

    return rows
  }

  private fun generateTagsMapping(
    tags: List<Tag>,
    bookmarks: List<Bookmark>
  ): Map<Long, List<Bookmark>> {
    val seenBookmarks = mutableSetOf<Long>()
    val tagMappings = mutableMapOf<Long, MutableList<Bookmark>>()

    for (tag in tags) {
      val matchingBookmarks = mutableListOf<Bookmark>()
      for (bookmark in bookmarks) {
        if (bookmark.tags.contains(tag.id)) {
          matchingBookmarks.add(bookmark)
          seenBookmarks.add(bookmark.id)
        }
      }
      tagMappings[tag.id] = matchingBookmarks
    }

    val untaggedBookmarks = mutableListOf<Bookmark>()
    for (bookmark in bookmarks) {
      if (!seenBookmarks.contains(bookmark.id)) {
        untaggedBookmarks.add(bookmark)
      }
    }
    tagMappings[BOOKMARKS_WITHOUT_TAGS_ID] = untaggedBookmarks

    return tagMappings
  }

  private fun generateTagMap(tags: List<Tag>): Map<Long, Tag> {
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
    private const val BOOKMARKS_WITHOUT_TAGS_ID: Long = -1
  }
}
