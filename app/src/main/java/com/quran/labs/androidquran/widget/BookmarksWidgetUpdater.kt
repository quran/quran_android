package com.quran.labs.androidquran.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.quran.labs.androidquran.R
import javax.inject.Inject

interface BookmarksWidgetUpdater {
  fun checkForAnyBookmarksWidgets(): Boolean
  fun updateBookmarksWidget()
}

class BookmarksWidgetUpdaterImpl @Inject constructor(private val context: Context): BookmarksWidgetUpdater {

  override fun checkForAnyBookmarksWidgets() =
      AppWidgetManager.getInstance(context)
          .getAppWidgetIds(ComponentName(context, BookmarksWidget::class.java))
          .isNotEmpty()

  override fun updateBookmarksWidget() {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, BookmarksWidget::class.java))
    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.list_view_widget)
  }
}
