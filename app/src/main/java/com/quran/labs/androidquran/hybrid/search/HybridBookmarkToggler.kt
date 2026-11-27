package com.quran.labs.androidquran.hybrid.search

import com.quran.data.dao.BookmarksDao
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@SingleIn(AppScope::class)
class HybridBookmarkToggler @Inject constructor(
  private val bookmarksDao: BookmarksDao
) {
  val bookmarkedKeysFlow: Flow<Set<Long>> = bookmarksDao.bookmarksFlow()
    .map { bookmarks ->
      buildSet(bookmarks.size) {
        for (bookmark in bookmarks) {
          val sura = bookmark.sura
          val ayah = bookmark.ayah
          if (sura != null && ayah != null) {
            add(ayahBookmarkKey(sura, ayah))
          }
        }
      }
    }
    .distinctUntilChanged()

  suspend fun toggleAyahBookmark(sura: Int, ayah: Int): Boolean {
    if (sura <= 0 || ayah <= 0) {
      return false
    }
    val target = SuraAyah(sura, ayah)
    if (bookmarksDao.isSuraAyahBookmarked(target)) {
      bookmarksDao.deleteAyahBookmark(target)
      return false
    }
    val defaultCollectionId = bookmarksDao.tags()
      .firstOrNull { it.isDefault }
      ?.id
      ?: bookmarksDao.collectionsWithBookmarksFlow()
        .first()
        .firstOrNull { it.readingCollection.isDefault }
        ?.readingCollection
        ?.id
      ?: return false
    bookmarksDao.replaceAyahBookmarkCollections(target, setOf(defaultCollectionId))
    return bookmarksDao.isSuraAyahBookmarked(target)
  }
}
