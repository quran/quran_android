package com.quran.labs.androidquran.widgets

import android.content.Intent
import android.widget.RemoteViewsService

class WidgetService : RemoteViewsService() {
  /*
* So pretty simple just defining the Adapter of the listview
* here Adapter is ListProvider
* */
  override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
    return WidgetListProvider(applicationContext)
  }
}
