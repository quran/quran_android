package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult
import com.quran.labs.androidquran.dao.bookmark.BookmarkResult
import com.quran.labs.androidquran.dao.bookmark.BookmarkRowData
import com.quran.labs.androidquran.data.QuranDisplayData
import javax.inject.Inject

class BookmarkUIConverter @Inject constructor(
  private val quranRowFactory: QuranRowFactory,
  private val quranInfo: QuranInfo
) {

  fun convertToUIResult(context: Context, rawResult: BookmarkRawResult): BookmarkResult {
    val uiRows = rawResult.rows.map { rowData ->
      when (rowData) {
        is BookmarkRowData.RecentPageHeader -> 
          quranRowFactory.fromRecentPageHeader(context, rowData.count)
          
        is BookmarkRowData.RecentPage -> {
          val page = if (quranInfo.isValidPage(rowData.recentPage.page)) {
            rowData.recentPage.page
          } else {
            1
          }
          quranRowFactory.fromCurrentPage(context, page, rowData.recentPage.timestamp)
        }
        
        is BookmarkRowData.TagHeader -> 
          quranRowFactory.fromTag(rowData.tag)
          
        is BookmarkRowData.BookmarkItem -> 
          quranRowFactory.fromBookmark(context, rowData.bookmark, rowData.tagId)
          
        is BookmarkRowData.PageBookmarksHeader -> 
          quranRowFactory.fromPageBookmarksHeader(context)
          
        is BookmarkRowData.AyahBookmarksHeader -> 
          quranRowFactory.fromAyahBookmarksHeader(context)
          
        is BookmarkRowData.NotTaggedHeader -> 
          QuranRowFactory.fromNotTaggedHeader(context)
      }
    }
    
    return BookmarkResult(uiRows, rawResult.tagMap)
  }
}