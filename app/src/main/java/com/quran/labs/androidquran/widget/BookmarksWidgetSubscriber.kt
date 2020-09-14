package com.quran.labs.androidquran.widget

import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import javax.inject.Inject
import javax.inject.Singleton


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
