package com.quran.labs.androidquran.presenter.bookmark

import androidx.annotation.VisibleForTesting
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.dao.RecentPagesDao
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.BookmarkItem
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.NotTaggedHeader
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.ReadingBookmarkHeader
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.ReadingBookmarkItem
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.RecentPageHeader
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData.TagHeader
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.bookmark.model.isDefaultBookmarkCollectionId
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.observers.DisposableSingleObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

open class BookmarkPresenter @Inject internal constructor(
  private val bookmarksDao: BookmarksDao,
  private val recentPagesDao: RecentPagesDao,
  private val readingBookmarksDao: ReadingBookmarksDao,
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

  fun shouldShowInlineTags(): Boolean = !isGroupedByTags

  fun getContextualOperationsForItems(rows: List<QuranRow>): BooleanArray {
    val headers = rows.count { row -> row.isBookmarkHeader && row.userTagId() != null }
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
    val cachedRows = currentData.rows

    val bookmarkIdsToRemove = mutableSetOf<String>()
    val tagIdsToUntag = mutableSetOf<String>()
    val bookmarkTagContext = mutableMapOf<String, MutableSet<String>>()

    for (row in remove) {
      val bookmarkId = row.bookmarkId
      val tagId = row.userTagId()
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
    val removedBookmarks = mutableSetOf<Bookmark>()
    var haveUntaggedSection = false

    for (rowData in cachedRows) {
      when (rowData) {
        is BookmarkItem -> {
          val bookmarkId = rowData.bookmark.id
          val currentTagId = rowData.tagId?.takeUnless { tagId -> tagId.isDefaultBookmarkCollectionId() }
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
        val tagId = row.userTagId()
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

  private suspend fun getBookmarksWithAyat(sortOrder: Int): BookmarkData {
    return coroutineScope {
      val bookmarkData = async { getBookmarkData(sortOrder) }
      val recentPages = async { getRecentPages() }

      hydrateAyahText(
        bookmarkData.await().copy(recentPages = recentPages.await())
      )
    }
  }

  private suspend fun hydrateAyahText(bookmarkData: BookmarkData): BookmarkData {
    return withContext(Dispatchers.IO) {
      try {
        bookmarkData.copy(
          bookmarks = arabicDatabaseUtils().hydrateAyahText(bookmarkData.bookmarks.toMutableList())
        )
      } catch (exception: Exception) {
        bookmarkData
      }
    }
  }

  private suspend fun getRecentPages(): List<RecentPage> {
    return withContext(Dispatchers.IO) { recentPagesDao.recentPages() }
  }

  @VisibleForTesting
  suspend fun getBookmarksList(sortOrder: Int, groupByTags: Boolean): BookmarkRawResult {
    return coroutineScope {
      val bookmarkData = async { getBookmarksWithAyat(sortOrder) }
      val readingBookmark = async { readingBookmarksDao.readingBookmark() }
      val data = bookmarkData.await()
      val rows = getBookmarkRowData(data, groupByTags, readingBookmark.await())
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
    groupByTags: Boolean,
    readingBookmark: ReadingBookmark?
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
    if (readingBookmark != null) {
      rows.add(0, ReadingBookmarkHeader)
      rows.add(1, ReadingBookmarkItem(readingBookmark))
    }
    return rows
  }

  private fun getRowDataSortedByTags(
    tags: List<Tag>,
    bookmarks: List<Bookmark>
  ): MutableList<BookmarkRowData> {
    val rows = mutableListOf<BookmarkRowData>()
    val ayahBookmarks = bookmarks.filterNot { bookmark -> bookmark.isPageBookmark() }
    val tagsMapping = generateTagsMapping(tags, ayahBookmarks)

    for (tag in tags) {
      rows.add(TagHeader(tag))
      val tagBookmarks = tagsMapping.byTagId[tag.id].orEmpty()
      for (bookmark in tagBookmarks) {
        rows.add(BookmarkItem(bookmark, tag.id))
      }
    }

    val untagged = tagsMapping.bookmarksWithoutUserTags
    if (untagged.isNotEmpty()) {
      rows.add(NotTaggedHeader)
      for (bookmark in untagged) {
        rows.add(BookmarkItem(bookmark, null))
      }
    }
    return rows
  }

  private fun getSortedRowData(bookmarks: List<Bookmark>): MutableList<BookmarkRowData> {
    val ayahBookmarks = bookmarks.filterNot { bookmark -> bookmark.isPageBookmark() }
    val rows = mutableListOf<BookmarkRowData>()
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
  ): TagsMapping {
    val seenBookmarks = mutableSetOf<String>()
    val tagMappings = mutableMapOf<String, MutableList<Bookmark>>()

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

    return TagsMapping(tagMappings, untaggedBookmarks)
  }

  private fun generateTagMap(tags: List<Tag>): Map<String, Tag> {
    return tags.associateByTo(mutableMapOf()) { it.id }
  }

  private fun QuranRow.userTagId(): String? {
    return tagId?.takeUnless { tagId -> tagId.isDefaultBookmarkCollectionId() }
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

  private data class TagsMapping(
    val byTagId: Map<String, List<Bookmark>>,
    /**
     * The default collection is not exposed as a user tag, so this is the visible "no user tags"
     * section rather than a persisted collection ID.
     */
    val bookmarksWithoutUserTags: List<Bookmark>
  )
}
