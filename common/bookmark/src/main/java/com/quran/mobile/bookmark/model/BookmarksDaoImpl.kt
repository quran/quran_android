package com.quran.mobile.bookmark.model

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quran.data.dao.BookmarksDao
import com.quran.data.dao.RecentPagesDao
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.BookmarksDatabase
import com.quran.mobile.bookmark.mapper.Mappers
import com.quran.mobile.bookmark.mapper.convergeCommonlyTagged
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
class BookmarksDaoImpl @Inject constructor(
  bookmarksDatabase: BookmarksDatabase,
  private val recentPagesDaoProvider: Provider<RecentPagesDao>
) : BookmarksDao {
  private val bookmarkQueries = bookmarksDatabase.bookmarkQueries
  private val bookmarkTagQueries = bookmarksDatabase.bookmarkTagQueries

  private val internalChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  override val changes: Flow<Unit> = internalChanges

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
      internalChanges.emit(Unit)
    }
  }

  override suspend fun removeBookmarksForPage(page: Int) {
    withContext(Dispatchers.IO) {
      val bookmarkId = bookmarkQueries.getBookmarkIdForPage(page).executeAsOneOrNull()
      if (bookmarkId != null) {
        bookmarkTagQueries.deleteByBookmarkIds(listOf(bookmarkId))
        bookmarkQueries.deleteByIds(listOf(bookmarkId))
        internalChanges.emit(Unit)
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
        internalChanges.emit(Unit)
        false
      } else {
        bookmarkQueries.addBookmark(null, null, page)
        internalChanges.emit(Unit)
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
        internalChanges.emit(Unit)
        false
      } else {
        bookmarkQueries.addBookmark(suraAyah.sura, suraAyah.ayah, page)
        internalChanges.emit(Unit)
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
    return recentPagesDaoProvider().recentPages()
  }

  override suspend fun replaceRecentPages(pages: List<RecentPage>) {
    recentPagesDaoProvider().replaceRecentPages(pages)
  }

  override suspend fun removeRecentPages() {
    recentPagesDaoProvider().removeRecentPages()
  }

  override suspend fun removeRecentsForPage(page: Int) {
    recentPagesDaoProvider().removeRecentsForPage(page)
  }
}
