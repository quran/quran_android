package com.quran.labs.androidquran.fakes

import androidx.core.util.Pair
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag

/**
 * Fake implementation of BookmarksDBAdapter for testing.
 *
 * NOTE: Since BookmarksDBAdapter is final and cannot be extended, this is a standalone
 * fake that mimics its interface. Tests should inject this fake instead of the real adapter.
 *
 * This fake maintains in-memory state for bookmarks, tags, and recent pages.
 * It provides configurable behavior for testing various scenarios.
 *
 * Usage:
 * ```
 * val fake = FakeBookmarksDBAdapter()
 * fake.setTags(listOf(Tag(1, "Important")))
 * fake.setBookmarks(listOf(bookmark1, bookmark2))
 * fake.setRecentPages(listOf(RecentPage(42, timestamp)))
 *
 * // Configure behavior
 * fake.setUpdateTagResult(false) // Force update to fail
 *
 * // Assertions
 * fake.assertTagUpdated(tagId = 1, newName = "Updated")
 * fake.assertRecentPageAdded(page = 42)
 * ```
 */
class FakeBookmarksDBAdapter {

  // In-memory state
  private val tags = mutableListOf<Tag>()
  private val bookmarks = mutableListOf<Bookmark>()
  private val recentPages = mutableListOf<RecentPage>()
  private val bookmarkTags = mutableMapOf<Long, MutableList<Long>>() // bookmarkId -> list of tagIds

  // Configurable behavior
  private var updateTagResult = true
  private var tagBookmarksResult = true
  private var importBookmarksResult = true

  // Call tracking for assertions
  private val updateTagCalls = mutableListOf<UpdateTagCall>()
  private val addRecentPageCalls = mutableListOf<Int>()
  private val replaceRecentRangeCalls = mutableListOf<ReplaceRangeCall>()
  private val tagBookmarksCalls = mutableListOf<TagBookmarksCall>()
  private val bulkDeleteCalls = mutableListOf<BulkDeleteCall>()

  // Data classes for call tracking
  data class UpdateTagCall(val tagId: Long, val newName: String)
  data class ReplaceRangeCall(val startPage: Int, val endPage: Int, val page: Int)
  data class TagBookmarksCall(val bookmarkIds: LongArray, val tagIds: Set<Long>, val deleteNonTagged: Boolean)
  data class BulkDeleteCall(val tagIds: List<Long>, val bookmarkIds: List<Long>, val untag: List<Pair<Long, Long>>)

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

  fun setUpdateTagResult(result: Boolean) {
    updateTagResult = result
  }

  fun setTagBookmarksResult(result: Boolean) {
    tagBookmarksResult = result
  }

  fun setImportBookmarksResult(result: Boolean) {
    importBookmarksResult = result
  }

  // Public API methods matching BookmarksDBAdapter
  fun getRecentPages(): List<RecentPage> {
    return recentPages.toList()
  }

  fun addRecentPage(page: Int) {
    addRecentPageCalls.add(page)
    val timestamp = System.currentTimeMillis()
    recentPages.add(0, RecentPage(page, timestamp))
  }

  fun replaceRecentRangeWithPage(deleteRangeStart: Int, deleteRangeEnd: Int, page: Int) {
    replaceRecentRangeCalls.add(ReplaceRangeCall(deleteRangeStart, deleteRangeEnd, page))

    // Remove pages in the range
    recentPages.removeIf { it.page in deleteRangeStart..deleteRangeEnd }

    // Add the new page
    val timestamp = System.currentTimeMillis()
    recentPages.add(0, RecentPage(page, timestamp))
  }

  fun updateTag(id: Long, newName: String): Boolean {
    updateTagCalls.add(UpdateTagCall(id, newName))

    if (!updateTagResult) {
      return false
    }

    // Check if name already exists (would fail)
    if (tags.any { it.name == newName && it.id != id }) {
      return false
    }

    // Update the tag
    val index = tags.indexOfFirst { it.id == id }
    if (index >= 0) {
      tags[index] = Tag(id, newName)
      return true
    }
    return false
  }

  fun tagBookmarks(bookmarkIds: LongArray, tagIds: Set<Long>, deleteNonTagged: Boolean): Boolean {
    tagBookmarksCalls.add(TagBookmarksCall(bookmarkIds, tagIds, deleteNonTagged))

    if (!tagBookmarksResult) {
      return false
    }

    for (bookmarkId in bookmarkIds) {
      if (deleteNonTagged) {
        bookmarkTags[bookmarkId] = mutableListOf()
      }

      val currentTags = bookmarkTags.getOrPut(bookmarkId) { mutableListOf() }
      for (tagId in tagIds) {
        if (!currentTags.contains(tagId)) {
          currentTags.add(tagId)
        }
      }
    }

    return true
  }

  fun bulkDelete(tagIds: List<Long>, bookmarkIds: List<Long>, untag: List<Pair<Long, Long>>) {
    bulkDeleteCalls.add(BulkDeleteCall(tagIds, bookmarkIds, untag))

    // Remove tags
    tags.removeIf { it.id in tagIds }

    // Remove bookmarks
    bookmarks.removeIf { it.id in bookmarkIds }

    // Remove bookmark-tag associations for deleted bookmarks
    bookmarkIds.forEach { bookmarkTags.remove(it) }

    // Remove specific tag associations (untag)
    untag.forEach { pair ->
      bookmarkTags[pair.first]?.remove(pair.second)
    }
  }

  fun getTags(): List<Tag> {
    return tags.toList()
  }

  fun getBookmarks(sortOrder: Int): List<Bookmark> {
    return when (sortOrder) {
      SORT_DATE_ADDED -> bookmarks.sortedByDescending { it.timestamp }
      SORT_LOCATION -> bookmarks.sortedBy { it.page }
      else -> bookmarks.toList()
    }
  }

  fun getBookmarkedAyahsOnPage(page: Int): List<Bookmark> {
    return bookmarks.filter { it.page == page && !it.isPageBookmark() }
  }

  fun getBookmarkTagIds(bookmarkId: Long): List<Long> {
    return bookmarkTags[bookmarkId]?.toList() ?: emptyList()
  }

  fun getBookmarkId(sura: Int?, ayah: Int?, page: Int): Long {
    val bookmark = bookmarks.find {
      it.sura == sura && it.ayah == ayah && it.page == page
    }
    return bookmark?.id ?: -1L
  }

  fun addBookmark(sura: Int?, ayah: Int?, page: Int): Long {
    val newId = (bookmarks.maxOfOrNull { it.id } ?: 0) + 1
    val bookmark = Bookmark(newId, sura, ayah, page, System.currentTimeMillis())
    bookmarks.add(bookmark)
    return newId
  }

  fun addBookmarkIfNotExists(sura: Int?, ayah: Int?, page: Int): Long {
    val existingId = getBookmarkId(sura, ayah, page)
    return if (existingId >= 0) {
      existingId
    } else {
      addBookmark(sura, ayah, page)
    }
  }

  fun removeBookmark(bookmarkId: Long) {
    bookmarks.removeIf { it.id == bookmarkId }
    bookmarkTags.remove(bookmarkId)
  }

  fun addTag(name: String): Long {
    val existingTag = tags.find { it.name == name }
    if (existingTag != null) {
      return existingTag.id
    }

    val newId = (tags.maxOfOrNull { it.id } ?: 0) + 1
    tags.add(Tag(newId, name))
    return newId
  }

  fun importBookmarks(data: BookmarkData): Boolean {
    if (!importBookmarksResult) {
      return false
    }

    // Clear all existing data
    tags.clear()
    bookmarks.clear()
    bookmarkTags.clear()

    // Import new data
    tags.addAll(data.tags)
    bookmarks.addAll(data.bookmarks)

    // Rebuild bookmark-tag associations
    data.bookmarks.forEach { bookmark ->
      bookmarkTags[bookmark.id] = bookmark.tags.toMutableList()
    }

    return true
  }

  // Assertion methods
  fun assertTagUpdated(tagId: Long, newName: String) {
    val call = updateTagCalls.find { it.tagId == tagId && it.newName == newName }
    if (call == null) {
      throw AssertionError(
        "Expected updateTag($tagId, $newName) but was not called. " +
        "Actual calls: $updateTagCalls"
      )
    }
  }

  fun assertRecentPageAdded(page: Int) {
    if (!addRecentPageCalls.contains(page)) {
      throw AssertionError(
        "Expected addRecentPage($page) but was not called. " +
        "Actual calls: $addRecentPageCalls"
      )
    }
  }

  fun assertReplaceRecentRangeCalled(startPage: Int, endPage: Int, page: Int) {
    val call = replaceRecentRangeCalls.find {
      it.startPage == startPage && it.endPage == endPage && it.page == page
    }
    if (call == null) {
      throw AssertionError(
        "Expected replaceRecentRangeWithPage($startPage, $endPage, $page) but was not called. " +
        "Actual calls: $replaceRecentRangeCalls"
      )
    }
  }

  fun assertTagBookmarksCalled(bookmarkIds: LongArray, tagIds: Set<Long>, deleteNonTagged: Boolean) {
    val call = tagBookmarksCalls.find {
      it.bookmarkIds.contentEquals(bookmarkIds) &&
      it.tagIds == tagIds &&
      it.deleteNonTagged == deleteNonTagged
    }
    if (call == null) {
      throw AssertionError(
        "Expected tagBookmarks(${bookmarkIds.toList()}, $tagIds, $deleteNonTagged) but was not called. " +
        "Actual calls: ${tagBookmarksCalls.map { "(${it.bookmarkIds.toList()}, ${it.tagIds}, ${it.deleteNonTagged})" }}"
      )
    }
  }

  fun getUpdateTagCallCount(): Int = updateTagCalls.size
  fun getAddRecentPageCallCount(): Int = addRecentPageCalls.size
  fun getReplaceRecentRangeCallCount(): Int = replaceRecentRangeCalls.size
  fun getTagBookmarksCallCount(): Int = tagBookmarksCalls.size
  fun getBulkDeleteCallCount(): Int = bulkDeleteCalls.size

  // Clear methods for test isolation
  fun clearCallHistory() {
    updateTagCalls.clear()
    addRecentPageCalls.clear()
    replaceRecentRangeCalls.clear()
    tagBookmarksCalls.clear()
    bulkDeleteCalls.clear()
  }

  fun reset() {
    tags.clear()
    bookmarks.clear()
    recentPages.clear()
    bookmarkTags.clear()
    clearCallHistory()
    updateTagResult = true
    tagBookmarksResult = true
    importBookmarksResult = true
  }

  companion object {
    const val SORT_DATE_ADDED = 0
    const val SORT_LOCATION = 1
  }
}
