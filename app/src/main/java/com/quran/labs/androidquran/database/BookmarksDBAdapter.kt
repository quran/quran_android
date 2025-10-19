package com.quran.labs.androidquran.database

import androidx.core.util.Pair
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.labs.androidquran.data.Constants
import com.quran.mobile.bookmark.mapper.Mappers
import com.quran.mobile.bookmark.mapper.convergeCommonlyTagged
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Deprecated("Use BookmarksDao instead")
@SingleIn(AppScope::class)
class BookmarksDBAdapter @Inject constructor(bookmarksDatabase: BookmarksDatabase) {
  private val tagQueries = bookmarksDatabase.tagQueries
  private val bookmarkQueries = bookmarksDatabase.bookmarkQueries
  private val lastPageQueries = bookmarksDatabase.lastPageQueries
  private val bookmarkTagQueries = bookmarksDatabase.bookmarkTagQueries

  companion object {
    const val SORT_DATE_ADDED = 0
    const val SORT_LOCATION = 1
  }

  fun getBookmarkedAyahsOnPage(page: Int): List<Bookmark> = getBookmarks(SORT_LOCATION, page)

  fun getBookmarks(sortOrder: Int): List<Bookmark> = getBookmarks(sortOrder, null)

  private fun getBookmarks(sortOrder: Int, pageFilter: Int?): List<Bookmark> {
    val query = when {
      pageFilter != null -> {
        bookmarkQueries.getBookmarksByPage(pageFilter, Mappers.bookmarkWithTagMapper)
      }
      sortOrder == SORT_LOCATION -> {
        bookmarkQueries.getBookmarksByLocation(Mappers.bookmarkWithTagMapper)
      }
      else -> {
        bookmarkQueries.getBookmarksByDateAdded(Mappers.bookmarkWithTagMapper)
      }
    }
    return query.executeAsList().convergeCommonlyTagged()
  }

  fun getRecentPages(): List<RecentPage> {
    return lastPageQueries.getLastPages(Mappers.recentPageMapper)
      .executeAsList()
  }

  fun replaceRecentRangeWithPage(deleteRangeStart: Int, deleteRangeEnd: Int, page: Int) {
    val maxPages = Constants.MAX_RECENT_PAGES.toLong()
    lastPageQueries.replaceRangeWithPage(deleteRangeStart, deleteRangeEnd, page, maxPages)
  }

  fun addRecentPage(page: Int) {
    val maxPages = Constants.MAX_RECENT_PAGES.toLong()
    lastPageQueries.addLastPage(page, maxPages)
  }

  fun getBookmarkTagIds(bookmarkId: Long): List<Long> {
    return bookmarkTagQueries.getTagIdsForBookmark(bookmarkId)
      .executeAsList()
  }

  fun getBookmarkId(sura: Int?, ayah: Int?, page: Int): Long {
    val query = if (sura != null && ayah != null) {
      bookmarkQueries.getBookmarkIdForSuraAyah(sura, ayah)
    } else {
      bookmarkQueries.getBookmarkIdForPage(page)
    }
    return query.executeAsOneOrNull() ?: -1L
  }

  fun bulkDelete(tagIds: List<Long>, bookmarkIds: List<Long>, untag: List<Pair<Long, Long>>) {
    bookmarkQueries.transaction {
      if (tagIds.isNotEmpty()) {
        bookmarkTagQueries.deleteByTagIds(tagIds)
        tagQueries.deleteByIds(tagIds)
      }

      if (bookmarkIds.isNotEmpty()) {
        bookmarkQueries.deleteByIds(bookmarkIds)
      }

      untag.forEach { bookmarkTagQueries.untag(it.first, it.second) }
    }
  }

  fun addBookmarkIfNotExists(sura: Int?, ayah: Int?, page: Int): Long {
    var bookmarkId = getBookmarkId(sura, ayah, page)
    if (bookmarkId < 0) {
      bookmarkId = addBookmark(sura, ayah, page)
    }
    return bookmarkId
  }

  fun addBookmark(sura: Int?, ayah: Int?, page: Int): Long {
    bookmarkQueries.addBookmark(sura, ayah, page)
    return getBookmarkId(sura, ayah, page)
  }

  fun removeBookmark(bookmarkId: Long) {
    bookmarkTagQueries.transaction {
      bookmarkTagQueries.deleteByBookmarkIds(listOf(bookmarkId))
      bookmarkQueries.deleteByIds(listOf(bookmarkId))
    }
  }

  fun getTags(): List<Tag> {
    return tagQueries.getTags(Mappers.tagMapper).executeAsList()
  }

  fun addTag(name: String): Long {
    val existingTag = haveMatchingTag(name)
    return if (existingTag == -1L) {
      tagQueries.addTag(name)
      haveMatchingTag(name)
    } else {
      existingTag
    }
  }

  private fun haveMatchingTag(name: String): Long {
    return tagQueries.tagByName(name).executeAsOneOrNull()?._ID ?: -1L
  }

  fun updateTag(id: Long, newName: String): Boolean {
    val existingTag = haveMatchingTag(newName)
    return if (existingTag == -1L) {
      tagQueries.updateTag(newName, id)
      true
    } else {
      false
    }
  }

  /**
   * Tag a list of bookmarks with a list of tags.
   * @param bookmarkIds the list of bookmark ids to tag.
   * @param tagIds the tags to tag those bookmarks with.
   * @param deleteNonTagged whether or not we should delete all tags not in tagIds from those
   * bookmarks or not.
   * @return a boolean denoting success
   */
  fun tagBookmarks(bookmarkIds: LongArray, tagIds: Set<Long>, deleteNonTagged: Boolean): Boolean {
    bookmarkTagQueries.transaction {
      if (deleteNonTagged) {
        bookmarkTagQueries.deleteByBookmarkIds(bookmarkIds.toList())
      }

      for (tagId in tagIds) {
        for (bookmarkId in bookmarkIds) {
          bookmarkTagQueries.replaceBookmarkTag(bookmarkId, tagId)
        }
      }
    }
    return true
  }

  fun importBookmarks(data: BookmarkData): Boolean {
    bookmarkQueries.transaction {
      bookmarkTagQueries.deleteAll()
      tagQueries.deleteAll()
      bookmarkQueries.deleteAll()

      data.tags.forEach { tagQueries.restoreTag(it.id, it.name, System.currentTimeMillis()) }
      data.bookmarks.forEach { bookmark ->
        bookmarkQueries.restoreBookmark(
          bookmark.id,
          bookmark.sura,
          bookmark.ayah,
          bookmark.page,
          bookmark.timestamp
        )
        bookmark.tags.forEach { tagId -> bookmarkTagQueries.addBookmarkTag(bookmark.id, tagId) }
      }
    }
    return true
  }
}
