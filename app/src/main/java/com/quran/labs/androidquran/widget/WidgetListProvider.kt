package com.quran.labs.androidquran.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.ui.helpers.QuranRowFactory
import javax.inject.Inject

class WidgetListProvider(private val context: Context) : RemoteViewsFactory {

  private var quranRowList: List<QuranRow> = listOf()

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranRowFactory: QuranRowFactory

  init {
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
    populateListItem()
  }

  private fun populateListItem() {
    val appContext = context.applicationContext
    val mBookmarksDBAdapter = BookmarksDBAdapter(appContext, quranInfo.numberOfPages)
    val bookmarksList = mBookmarksDBAdapter.getBookmarks(BookmarksDBAdapter.SORT_LOCATION)
    quranRowList = bookmarksList.map { quranRowFactory.fromBookmark(appContext, it) }
  }

  override fun onCreate() = Unit
  override fun onDestroy() = Unit

  override fun onDataSetChanged() {
    populateListItem()
  }

  override fun getCount() = quranRowList.size

  override fun getItemId(position: Int) = position.toLong()

  override fun hasStableIds() = false

  /*
  *Similar to getView of Adapter where instead of View
  *we return RemoteViews
  *
  */
  override fun getViewAt(position: Int): RemoteViews {
    val remoteView = RemoteViews(context.packageName, R.layout.bookmarks_widget_list_row)
    val item = quranRowList[position]
    remoteView.setTextViewText(R.id.sura_title, item.text)
    remoteView.setTextViewText(R.id.sura_meta_data, item.metadata)
    remoteView.setImageViewResource(R.id.widget_favorite_icon, item.imageResource)
    if (item.imageFilterColor != null) {
      remoteView.setInt(R.id.widget_favorite_icon, "setColorFilter", item.imageFilterColor)
    }
    val fillInIntent = Intent().apply {
      putExtras(Bundle().apply {
        putInt("page", item.bookmark.page)
        putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, item.bookmark.sura)
        putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, item.bookmark.ayah)
      })
    }
    remoteView.setOnClickFillInIntent(R.id.widget_item, fillInIntent)
    return remoteView
  }

  override fun getLoadingView() = null

  override fun getViewTypeCount() = 1
}
