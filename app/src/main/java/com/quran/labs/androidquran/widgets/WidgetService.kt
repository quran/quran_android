package com.quran.labs.androidquran.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService

class WidgetService : RemoteViewsService() {
  /*
* So pretty simple just defining the Adapter of the listview
* here Adapter is ListProvider
* */
  override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
    val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID)
    return WidgetListProvider(applicationContext, intent)
  }
}
