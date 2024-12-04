package com.quran.mobile.bookmark.model

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quran.data.dao.BookmarksDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.mapper.Mappers
import com.quran.mobile.bookmark.mapper.convergeCommonlyTagged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BookmarksDaoImpl @Inject constructor(
  bookmarksDatabase: BookmarksDatabase
) : BookmarksDao {
  private val bookmarkQueries = bookmarksDatabase.bookmarkQueries
  private val lastPageQueries = bookmarksDatabase.lastPageQueries
  private val bookmarkTagQueries = bookmarksDatabase.bookmarkTagQueries

  override suspend fun bookmarks(): List<Bookmark> {
    return withContext(Dispatchers.IO) {
      bookmarkQueries.getBookmarksByDateAdded(Mappers.bookmarkWithTagMapper)
        .executeAsList()
        .convergeCommonlyTagged()
    }
  }

  override fun bookmarksForPage(page: Int): Flow<List<Bookmark>> {
    return bookmarkQueries.getBookmarksByPage(page, Mappers.bookmarkWithTagMapper)
      .asFlow()
      .mapToList(Dispatchers.IO)
  }

  override fun pageBookmarksWithoutTags(): Flow<List<Bookmark>> {
    return bookmarkQueries.getPageBookmarksWithoutTags(Mappers.pageBookmarkMapper)
      .asFlow()
      .mapToList(Dispatchers.IO)
  }

  override suspend fun replaceBookmarks(bookmarks: List<Bookmark>) {
    withContext(Dispatchers.IO) {
      bookmarkQueries.transaction {
        bookmarks.forEach {
          bookmarkQueries.update(it.sura, it.ayah, it.page, it.id)
        }
      }
    }
  }

  override suspend fun removeBookmarksForPage(page: Int) {
    withContext(Dispatchers.IO) {
      val bookmarkId = bookmarkQueries.getBookmarkIdForPage(page).executeAsOneOrNull()
      if (bookmarkId != null) {
        bookmarkTagQueries.deleteByBookmarkIds(listOf(bookmarkId))
        bookmarkQueries.deleteByIds(listOf(bookmarkId))
      }
    }
  }

  override suspend fun isSuraAyahBookmarked(suraAyah: SuraAyah): Boolean {
    val bookmarkIds = bookmarkQueries.getBookmarkIdForSuraAyah(suraAyah.sura, suraAyah.ayah)
      .asFlow()
      // was .mapToOneOrNull, but some people have multiple bookmarks for the same ayah
      // should try to figure out why at some point or otherwise de-duplicate them
      .mapToList(Dispatchers.IO)
      .first()
    return bookmarkIds.isNotEmpty()
  }

  override suspend fun togglePageBookmark(page: Int): Boolean {
    return withContext(Dispatchers.IO) {
      val bookmarkIds = bookmarkQueries.getBookmarkIdForPage(page).executeAsList()
      if (bookmarkIds.size > 1) {
        bookmarkIds.drop(1).forEach { deleteBookmarkById(it) }
      }

      val bookmarkId = bookmarkIds.firstOrNull()
      if (bookmarkId != null) {
        deleteBookmarkById(bookmarkId)
        false
      } else {
        bookmarkQueries.addBookmark(null, null, page)
        true
      }
    }
  }

  override suspend fun toggleAyahBookmark(suraAyah: SuraAyah, page: Int): Boolean {
    return withContext(Dispatchers.IO) {
      val bookmarkId = bookmarkQueries.getBookmarkIdForSuraAyah(suraAyah.sura, suraAyah.ayah)
        .executeAsOneOrNull()
      if (bookmarkId != null) {
        deleteBookmarkById(bookmarkId)
        false
      } else {
        bookmarkQueries.addBookmark(suraAyah.sura, suraAyah.ayah, page)
        true
      }
    }
  }

  private suspend fun deleteBookmarkById(bookmarkId: Long) {
    withContext(Dispatchers.IO) {
      bookmarkTagQueries.transaction {
        bookmarkTagQueries.deleteByBookmarkIds(listOf(bookmarkId))
        bookmarkQueries.deleteByIds(listOf(bookmarkId))
      }
    }
  }

  override suspend fun recentPages(): List<RecentPage> {
    return withContext(Dispatchers.IO) {
      lastPageQueries.getLastPages(Mappers.recentPageMapper)
        .executeAsList()
    }
  }

  override suspend fun replaceRecentPages(pages: List<RecentPage>) {
    withContext(Dispatchers.IO) {
      lastPageQueries.transaction {
        pages.forEach { addRecentPage(it.page) }
      }
    }
  }

  override suspend fun removeRecentPages() {
    withContext(Dispatchers.IO) {
      lastPageQueries.removeLastPages()
    }
  }

  override suspend fun removeRecentsForPage(page: Int) {
    withContext(Dispatchers.IO) {
      lastPageQueries.transaction {
        val lastPages = lastPageQueries.getLastPages().executeAsList()
        val lastPagesWithoutPage = lastPages.filter { it.page != page }
        if (lastPages.size != lastPagesWithoutPage.size) {
          lastPageQueries.removeLastPages()
          lastPagesWithoutPage.forEach { addRecentPage(it.page) }
        }
      }
    }
  }

  private fun addRecentPage(page: Int) {
    val maxPages = MAX_RECENT_PAGES.toLong()
    lastPageQueries.addLastPage(page, maxPages)
  }

  companion object {
    private const val MAX_RECENT_PAGES = 3
  }
}
