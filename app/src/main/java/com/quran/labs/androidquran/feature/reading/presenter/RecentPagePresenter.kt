package com.quran.labs.androidquran.feature.reading.presenter

import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.model.bookmark.RecentPageModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@ActivityScope
class RecentPagePresenter @Inject constructor(private val model: RecentPageModel) {
  private val scope = MainScope()

  private var lastPage = 0
  private var minimumPage = 0
  private var maximumPage = 0

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

  fun bind(pageFlow: Flow<Int>) {
    minimumPage = Constants.NO_PAGE
    maximumPage = Constants.NO_PAGE
    lastPage = Constants.NO_PAGE
    pageFlow
      .onEach { onPageChanged(it) }
      .launchIn(scope)
  }

  fun unbind() {
    scope.cancel()
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
