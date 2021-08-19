package com.quran.labs.androidquran.widget

import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that monitors for changes to bookmarks and triggers [BookmarksWidget] updates.
 * Bookmark changes are only monitored if at least one [BookmarksWidget] exists.
 */
@Singleton
class BookmarksWidgetSubscriber @Inject constructor(
    private val bookmarkModel: BookmarkModel,
    private val bookmarksWidgetUpdater: BookmarksWidgetUpdater
) {

  private var bookmarksWidgetDisposable: Disposable? = null

  fun subscribeBookmarksWidgetIfNecessary() {
    if (bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()) {
      subscribeBookmarksWidget()
      }
  }

  private fun subscribeBookmarksWidget() {
    bookmarksWidgetDisposable = bookmarkModel.bookmarksObservable()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { bookmarksWidgetUpdater.updateBookmarksWidget() }
  }

  fun onEnabledBookmarksWidget() {
    if (bookmarksWidgetDisposable == null) {
      subscribeBookmarksWidget()
    }
  }

  fun onDisabledBookmarksWidget() {
    bookmarksWidgetDisposable?.dispose()
  }
}
