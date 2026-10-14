package com.quran.labs.androidquran.feature.reading.presenter

import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.dao.RecentPagesDao
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.feature.reading.model.LatestPageTracker
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

@ActivityScope
class RecentPagePresenter @Inject constructor(
  private val recentPagesDao: RecentPagesDao,
  private val latestPageTracker: LatestPageTracker,
  @param:Named(DependencyInjectionConstants.CURRENT_PAGE_TYPE) private val pageType: String
) {
  private val scope = MainScope()
  private val saveMutex = Mutex()
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
    latestPageTracker.updateLatestPage(page, pageType)
    recentPage = when (val current = recentPage) {
      RecentPage.NoPage -> RecentPage.Page(page, page, page)
      is RecentPage.Page -> current.withUpdatedPage(page)
    }
  }

  fun onJump() {
    saveAndReset()
  }

  fun bind(pageFlow: Flow<Int>) {
    currentJob?.cancel()
    recentPage = RecentPage.NoPage
    currentJob = pageFlow
      .onEach { onPageChanged(it) }
      .launchIn(scope)
  }

  fun unbind() {
    currentJob?.cancel()
    currentJob = null
    saveAndReset()
  }

  private fun saveAndReset() {
    val lastRecent = recentPage
    if (lastRecent is RecentPage.Page) {
      recentPage = RecentPage.NoPage
      persist(lastRecent)
    }
  }

  private fun persist(lastRecent: RecentPage.Page) {
    scope.launch {
      saveMutex.withLock {
        if (lastRecent.minPage == lastRecent.maxPage) {
          recentPagesDao.addRecentPage(lastRecent.page)
        } else {
          recentPagesDao.replaceRecentRangeWithPage(
            lastRecent.minPage,
            lastRecent.maxPage,
            lastRecent.page
          )
        }
      }
    }
  }
}
