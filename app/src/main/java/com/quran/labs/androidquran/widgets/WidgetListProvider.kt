package com.quran.labs.androidquran.widgets

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import javax.inject.Inject

class WidgetListProvider(private val context: Context) : RemoteViewsFactory {

  private var suraPageItemList: List<SuraPageItem> = listOf()

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  init {
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
    populateListItem()
  }

  private fun populateListItem() {
    val appContext = context.applicationContext
    val mBookmarksDBAdapter = BookmarksDBAdapter(appContext, quranInfo.numberOfPages)
    val bookmarksList = mBookmarksDBAdapter.getBookmarks(BookmarksDBAdapter.SORT_DATE_ADDED)
    suraPageItemList = bookmarksList.filter { it.isPageBookmark() }.map {
      SuraPageItem(sura = quranDisplayData.getSuraNameString(appContext, it.page), page = it.page)
    }
  }

  override fun onCreate() = Unit
  override fun onDestroy() = Unit

  override fun onDataSetChanged() {
    populateListItem()
  }

  override fun getCount() = suraPageItemList.size

  override fun getItemId(position: Int) = position.toLong()

  override fun hasStableIds() = false

  /*
  *Similar to getView of Adapter where instead of View
  *we return RemoteViews
  *
  */
  override fun getViewAt(position: Int): RemoteViews {
    val remoteView = RemoteViews(context.packageName, R.layout.bookmarks_widget_list_row)
    val item = suraPageItemList[position]
    remoteView.setTextViewText(R.id.sura_title, item.sura)
    remoteView.setTextViewText(R.id.sura_meta_data, context.resources.getText(R.string.quran_page).toString() + " " + item.page)
    remoteView.setImageViewResource(R.id.widget_favorite_icon, R.drawable.ic_favorite)
    val fillInIntent = Intent().apply {
      putExtras(Bundle().apply {
        putInt("page", item.page)
      })
    }
    remoteView.setOnClickFillInIntent(R.id.widget_item, fillInIntent)
    return remoteView
  }

  override fun getLoadingView() = null

  override fun getViewTypeCount() = 1

  internal data class SuraPageItem(val sura: String, val page: Int)
}
