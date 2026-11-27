package com.quran.labs.androidquran.widget

import com.quran.data.dao.BookmarksDao
import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Singleton that monitors for changes to bookmarks and triggers [BookmarksWidget] updates.
 * Bookmark changes are only monitored if at least one [BookmarksWidget] exists.
 */
@SingleIn(AppScope::class)
class BookmarksWidgetSubscriber @Inject constructor(
  private val bookmarksDao: Provider<BookmarksDao>,
  private val bookmarksWidgetUpdater: BookmarksWidgetUpdater
) {
  private val lock = Any()
  private var scope: CoroutineScope? = null
  private var isSubscribed = false

  fun subscribeBookmarksWidgetIfNecessary() {
    if (bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()) {
      subscribeBookmarksWidget()
    }
  }

  private fun subscribeBookmarksWidget() {
    val subscriptionScope = synchronized(lock) {
      if (isSubscribed) {
        return
      }
      isSubscribed = true
      CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
    }

    bookmarksDao().changes
      .onEach { bookmarksWidgetUpdater.updateBookmarksWidget() }
      .launchIn(subscriptionScope)
  }

  fun onEnabledBookmarksWidget() {
    subscribeBookmarksWidget()
  }

  fun onDisabledBookmarksWidget() {
    synchronized(lock) {
      scope?.cancel()
      scope = null
      isSubscribed = false
    }
  }
}
