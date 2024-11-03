package com.quran.labs.androidquran.feature.reading.presenter

import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.model.bookmark.RecentPageModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@ActivityScope
class RecentPagePresenter @Inject constructor(private val model: RecentPageModel) {
  private val scope = MainScope()
  private var currentJob: Job? = null

  private sealed class RecentPage {
    data object NoPage : RecentPage()
    data class Page(val minPage: Int, val maxPage: Int, val page: Int) : RecentPage() {
      fun withUpdatedPage(page: Int): Page {
        return copy(minPage = min(page, minPage), maxPage = max(page, maxPage), page = page)
      }
    }
  }

  private var recentPage: RecentPage = RecentPage.NoPage

  private fun onPageChanged(page: Int) {
    model.updateLatestPage(page)
    recentPage = when (val current = recentPage) {
      RecentPage.NoPage -> RecentPage.Page(page, page, page)
      is RecentPage.Page -> current.withUpdatedPage(page)
    }
  }

  fun onJump() {
    saveAndReset()
  }

  fun bind(pageFlow: Flow<Int>) {
    recentPage = RecentPage.NoPage
    currentJob = pageFlow
      .onEach { onPageChanged(it) }
      .launchIn(scope)
  }

  fun unbind() {
    currentJob?.cancel()
    saveAndReset()
  }

  private fun saveAndReset() {
    val lastRecent = recentPage
    if (lastRecent is RecentPage.Page) {
      model.persistLatestPage(lastRecent.minPage, lastRecent.maxPage, lastRecent.page)
      recentPage = RecentPage.NoPage
    }
  }
}
