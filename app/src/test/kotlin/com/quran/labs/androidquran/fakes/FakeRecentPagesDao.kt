package com.quran.labs.androidquran.fakes

import com.quran.data.dao.RecentPagesDao
import com.quran.data.model.bookmark.RecentPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeRecentPagesDao : RecentPagesDao {
  private val recentPages = MutableStateFlow<List<RecentPage>>(emptyList())

  fun setRecentPages(pages: List<RecentPage>) {
    recentPages.value = pages
  }

  override fun recentPagesFlow(): Flow<List<RecentPage>> {
    return recentPages
  }

  override suspend fun recentPages(): List<RecentPage> {
    return recentPages.value
  }

  override suspend fun addRecentPage(page: Int) {
    recentPages.update { pages ->
      listOf(RecentPage(page, System.currentTimeMillis() / 1000)) +
        pages.filterNot { it.page == page }
    }
    trimRecents()
  }

  override suspend fun replaceRecentRangeWithPage(deleteRangeStart: Int, deleteRangeEnd: Int, page: Int) {
    recentPages.update { pages -> pages.filterNot { it.page in deleteRangeStart..deleteRangeEnd } }
    addRecentPage(page)
  }

  override suspend fun removeRecentPages() {
    recentPages.value = emptyList()
  }

  override suspend fun replaceRecentPages(pages: List<RecentPage>) {
    recentPages.value = pages.take(MAX_RECENT_PAGES)
  }

  override suspend fun removeRecentsForPage(page: Int) {
    recentPages.update { pages -> pages.filterNot { it.page == page } }
  }

  private fun trimRecents() {
    recentPages.update { pages -> pages.take(MAX_RECENT_PAGES) }
  }

  companion object {
    private const val MAX_RECENT_PAGES = 3
  }
}
