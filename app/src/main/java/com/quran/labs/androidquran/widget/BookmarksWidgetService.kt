package com.quran.labs.androidquran.widget

import android.content.Intent
import android.widget.RemoteViewsService

/**
 * Service to populate list of bookmarks in [BookmarksWidget]
 */
class BookmarksWidgetService : RemoteViewsService() {

  override fun onGetViewFactory(intent: Intent) = BookmarksWidgetListProvider(applicationContext)
}
