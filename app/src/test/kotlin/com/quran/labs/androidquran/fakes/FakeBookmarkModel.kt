package com.quran.labs.androidquran.fakes

import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.helpers.inMemoryBookmarksAdapter
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

/**
 * Fake implementation of BookmarkModel for testing.
 *
 * This fake provides configurable behavior for bookmark operations and tracks method calls.
 * Since BookmarkModel is open, we can extend it and override open methods.
 *
 * Usage:
 * ```
 * val fake = FakeBookmarkModel()
 * fake.setTags(listOf(Tag(1, "Important")))
 * fake.setBookmarks(listOf(bookmark1, bookmark2))
 * fake.setRecentPages(listOf(RecentPage(42, timestamp)))
 *
 * // Configure behavior
 * fake.setUpdateBookmarkTagsResult(false) // Force update to fail
 *
 * // Subscribe to observables
 * fake.tagsObservable().subscribe { updated -> ... }
 *
 * // Assertions
 * fake.assertUpdateBookmarkTagsCalled(longArrayOf(1, 2), setOf(1L), false)
 * ```
 */
open class FakeBookmarkModel : BookmarkModel(inMemoryBookmarksAdapter(), FakeRecentPageModel()) {

  // State
  private val tags = mutableListOf<Tag>()
  private val bookmarks = mutableListOf<Bookmark>()
  private val recentPages = mutableListOf<RecentPage>()
  private val bookmarkTagIds = mutableMapOf<Long, List<Long>>() // bookmarkId -> tagIds

  // Configurable results
  private var updateBookmarkTagsResult = true
  private var safeAddBookmarkResult = 1L

  // Call tracking
  private val updateBookmarkTagsCalls = mutableListOf<UpdateTagsCall>()
  private val safeAddBookmarkCalls = mutableListOf<AddBookmarkCall>()
  private val getBookmarkTagIdsCalls = mutableListOf<Long>()

  data class UpdateTagsCall(val bookmarkIds: List<Long>, val tagIds: Set<Long>, val deleteNonTagged: Boolean)
  data class AddBookmarkCall(val sura: Int?, val ayah: Int?, val page: Int)

  // Configuration methods
  fun setTags(newTags: List<Tag>) {
    tags.clear()
    tags.addAll(newTags)
  }

  fun setBookmarks(newBookmarks: List<Bookmark>) {
    bookmarks.clear()
    bookmarks.addAll(newBookmarks)
  }

  fun setRecentPages(pages: List<RecentPage>) {
    recentPages.clear()
    recentPages.addAll(pages)
  }

  fun setBookmarkTagIds(bookmarkId: Long, tagIds: List<Long>) {
    bookmarkTagIds[bookmarkId] = tagIds
  }

  fun setUpdateBookmarkTagsResult(result: Boolean) {
    updateBookmarkTagsResult = result
  }

  fun setSafeAddBookmarkResult(result: Long) {
    safeAddBookmarkResult = result
  }

  // Override open methods from BookmarkModel
  override val tagsObservable: Single<List<Tag>>
    get() = Single.just(tags.toList())

  override fun getBookmarkDataObservable(sortOrder: Int): Single<BookmarkData> {
    val sortedBookmarks = when (sortOrder) {
      0 -> bookmarks.sortedByDescending { it.timestamp } // SORT_DATE_ADDED
      1 -> bookmarks.sortedBy { it.page } // SORT_LOCATION
      else -> bookmarks
    }
    return Single.just(BookmarkData(tags.toList(), sortedBookmarks, recentPages.toList()))
  }

  override fun updateBookmarkTags(
    bookmarkIds: LongArray,
    tagIds: Set<Long>,
    deleteNonTagged: Boolean
  ): Observable<Boolean> {
    updateBookmarkTagsCalls.add(UpdateTagsCall(bookmarkIds.toList(), tagIds, deleteNonTagged))

    if (updateBookmarkTagsResult) {
      // Update in-memory state
      for (bookmarkId in bookmarkIds) {
        val currentTags = bookmarkTagIds[bookmarkId]?.toMutableList() ?: mutableListOf()

        if (deleteNonTagged) {
          currentTags.clear()
        }

        currentTags.addAll(tagIds)
        bookmarkTagIds[bookmarkId] = currentTags.distinct()
      }
    }

    return Observable.just(updateBookmarkTagsResult)
  }

  override fun safeAddBookmark(sura: Int?, ayah: Int?, page: Int): Observable<Long> {
    safeAddBookmarkCalls.add(AddBookmarkCall(sura, ayah, page))

    // Add to in-memory state
    val newBookmark = Bookmark(safeAddBookmarkResult, sura, ayah, page, System.currentTimeMillis())
    bookmarks.add(newBookmark)

    return Observable.just(safeAddBookmarkResult)
  }

  override fun getBookmarkTagIds(bookmarkIdSingle: Single<Long>): Maybe<List<Long>> {
    return bookmarkIdSingle
      .filter { bookmarkId -> bookmarkId > 0 }
      .map { bookmarkId ->
        getBookmarkTagIdsCalls.add(bookmarkId)
        bookmarkTagIds[bookmarkId] ?: emptyList()
      }
  }

  // Assertion methods
  fun assertUpdateBookmarkTagsCalled(bookmarkIds: LongArray, tagIds: Set<Long>, deleteNonTagged: Boolean) {
    val expectedIds = bookmarkIds.toList()
    val call = updateBookmarkTagsCalls.find {
      it.bookmarkIds == expectedIds && it.tagIds == tagIds && it.deleteNonTagged == deleteNonTagged
    }
    if (call == null) {
      throw AssertionError(
        "Expected updateBookmarkTags($expectedIds, $tagIds, $deleteNonTagged) but was not called. " +
        "Actual calls: ${updateBookmarkTagsCalls.map { "(${it.bookmarkIds}, ${it.tagIds}, ${it.deleteNonTagged})" }}"
      )
    }
  }

  fun assertSafeAddBookmarkCalled(sura: Int?, ayah: Int?, page: Int) {
    val call = safeAddBookmarkCalls.find {
      it.sura == sura && it.ayah == ayah && it.page == page
    }
    if (call == null) {
      throw AssertionError(
        "Expected safeAddBookmark($sura, $ayah, $page) but was not called. " +
        "Actual calls: $safeAddBookmarkCalls"
      )
    }
  }

  fun assertGetBookmarkTagIdsCalled(bookmarkId: Long) {
    if (!getBookmarkTagIdsCalls.contains(bookmarkId)) {
      throw AssertionError(
        "Expected getBookmarkTagIds($bookmarkId) but was not called. " +
        "Actual calls: $getBookmarkTagIdsCalls"
      )
    }
  }

  fun getUpdateBookmarkTagsCallCount(): Int = updateBookmarkTagsCalls.size
  fun getSafeAddBookmarkCallCount(): Int = safeAddBookmarkCalls.size
  fun getBookmarkTagIdsCallCount(): Int = getBookmarkTagIdsCalls.size

  fun getCurrentTags(): List<Tag> = tags.toList()
  fun getCurrentBookmarks(): List<Bookmark> = bookmarks.toList()
  fun getCurrentRecentPages(): List<RecentPage> = recentPages.toList()

  // Clear methods for test isolation
  fun clearCallHistory() {
    updateBookmarkTagsCalls.clear()
    safeAddBookmarkCalls.clear()
    getBookmarkTagIdsCalls.clear()
  }

  fun reset() {
    tags.clear()
    bookmarks.clear()
    recentPages.clear()
    bookmarkTagIds.clear()
    clearCallHistory()
    updateBookmarkTagsResult = true
    safeAddBookmarkResult = 1L
  }
}
