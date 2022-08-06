package com.quran.labs.androidquran.presenter.bookmark

import com.quran.labs.androidquran.data.Constants
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.model.bookmark.RecentPageModel
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.PagerActivity
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.observers.DisposableObserver
import javax.inject.Inject

@ActivityScope
class RecentPagePresenter @Inject constructor(private val model: RecentPageModel) : Presenter<PagerActivity> {

  private var lastPage = 0
  private var minimumPage = 0
  private var maximumPage = 0
  private var disposable: Disposable? = null

  private fun onPageChanged(page: Int) {
    model.updateLatestPage(page)
    lastPage = page
    when {
        minimumPage == Constants.NO_PAGE -> {
          minimumPage = page
          maximumPage = page
        }
        page < minimumPage -> {
          minimumPage = page
        }
        page > maximumPage -> {
          maximumPage = page
        }
    }
  }

  fun onJump() {
    saveAndReset()
  }

  override fun bind(what: PagerActivity) {
    minimumPage = Constants.NO_PAGE
    maximumPage = Constants.NO_PAGE
    lastPage = Constants.NO_PAGE
    disposable = what.viewPagerObservable
      .subscribeWith(object : DisposableObserver<Int>() {
        override fun onNext(value: Int) {
          onPageChanged(value)
        }

        override fun onError(e: Throwable) {}
        override fun onComplete() {}
      })
  }

  override fun unbind(what: PagerActivity) {
    disposable?.dispose()
    saveAndReset()
  }

  private fun saveAndReset() {
    if (minimumPage != Constants.NO_PAGE || maximumPage != Constants.NO_PAGE) {
      model.persistLatestPage(minimumPage, maximumPage, lastPage)
      minimumPage = Constants.NO_PAGE
      maximumPage = Constants.NO_PAGE
    }
    lastPage = Constants.NO_PAGE
  }
}
