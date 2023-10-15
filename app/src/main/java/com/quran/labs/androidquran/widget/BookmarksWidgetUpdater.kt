package com.quran.labs.androidquran.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.quran.labs.androidquran.R
import com.quran.mobile.di.qualifier.ApplicationContext
import javax.inject.Inject

/**
 * Interface to allow for checking for [BookmarksWidget]s and updating them.
 */
interface BookmarksWidgetUpdater {

  /**
   * Check if there's any [BookmarksWidget]s
   */
  fun checkForAnyBookmarksWidgets(): Boolean

  /**
   * Trigger updates of any [BookmarksWidget]s that currently exist
   */
  fun updateBookmarksWidget()
}

class BookmarksWidgetUpdaterImpl @Inject constructor(@ApplicationContext private val appContext: Context) :
  BookmarksWidgetUpdater {

  override fun checkForAnyBookmarksWidgets() =
    AppWidgetManager.getInstance(appContext)
      ?.getAppWidgetIds(ComponentName(appContext, BookmarksWidget::class.java))
      ?.isNotEmpty() == true

  override fun updateBookmarksWidget() {
    val appWidgetManager = AppWidgetManager.getInstance(appContext)
    if (appWidgetManager != null) {
      val appWidgetIds =
        appWidgetManager.getAppWidgetIds(ComponentName(appContext, BookmarksWidget::class.java))
      appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.list_view_widget)
    }
  }
}
