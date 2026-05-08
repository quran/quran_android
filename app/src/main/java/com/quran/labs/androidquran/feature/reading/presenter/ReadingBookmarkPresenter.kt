package com.quran.labs.androidquran.feature.reading.presenter

import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.ActivityScope
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@ActivityScope
class ReadingBookmarkPresenter @Inject constructor(
  private val readingBookmarksDao: ReadingBookmarksDao
) {
  private val scope = MainScope()
  private var currentJob: Job? = null
  private var currentPage: Int? = null
  private var screen: Screen? = null

  fun bind(pageFlow: Flow<Int>, screen: Screen) {
    currentJob?.cancel()
    this.screen = screen
    currentJob = combine(
      pageFlow,
      readingBookmarksDao.readingBookmarkFlow()
    ) { page, bookmark ->
      page to bookmark.isPageReadingBookmark(page)
    }
      .onEach { (page, isBookmarked) ->
        currentPage = page
        screen.setPageReadingBookmarkSelected(isBookmarked)
      }
      .launchIn(scope)
  }

  fun unbind(screen: Screen) {
    if (this.screen === screen) {
      this.screen = null
      currentPage = null
      currentJob?.cancel()
      currentJob = null
    }
  }

  fun togglePageReadingBookmark(page: Int) {
    scope.launch {
      val isBookmarked = readingBookmarksDao.togglePageReadingBookmark(page)
      if (currentPage == page) {
        screen?.setPageReadingBookmarkSelected(isBookmarked)
      }
    }
  }

  private fun ReadingBookmark?.isPageReadingBookmark(page: Int): Boolean {
    return this is PageReadingBookmark && this.page == page
  }

  interface Screen {
    fun setPageReadingBookmarkSelected(isBookmarked: Boolean)
  }
}
