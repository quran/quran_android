package com.quran.data.dao

import com.quran.data.model.bookmark.RecentPage
import kotlinx.coroutines.flow.Flow

interface RecentPagesDao {
  fun recentPagesFlow(): Flow<List<RecentPage>>
  suspend fun recentPages(): List<RecentPage>
  suspend fun addRecentPage(page: Int)
  suspend fun replaceRecentRangeWithPage(deleteRangeStart: Int, deleteRangeEnd: Int, page: Int)
  suspend fun removeRecentPages()
  suspend fun replaceRecentPages(pages: List<RecentPage>)
  suspend fun removeRecentsForPage(page: Int)
}
