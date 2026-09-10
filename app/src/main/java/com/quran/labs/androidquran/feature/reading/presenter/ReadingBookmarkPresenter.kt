package com.quran.labs.androidquran.feature.reading.presenter

import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.ActivityScope
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

@ActivityScope
class ReadingBookmarkPresenter @Inject constructor(
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val quranSettings: QuranSettings,
  private val appCoroutineScope: AppCoroutineScope
) {
  private val scope = MainScope()
  private var currentJob: Job? = null
  private var currentPage: Int? = null
  private var persistedBookmark: ReadingBookmark? = null
  private var pendingMove: PendingMove? = null
  private var screen: Screen? = null

  fun bind(pageFlow: Flow<Int>, screen: Screen) {
    currentJob?.cancel()
    this.screen = screen
    currentJob = combine(
      pageFlow,
      readingBookmarksDao.readingBookmarkFlow()
    ) { page, bookmark -> page to bookmark }
      .onEach { (page, bookmark) ->
        currentPage = page
        persistedBookmark = bookmark
        // a pending (not yet durably written) move already reflects the intended state visually -
        // don't let this now-stale flow emission flicker the icon back to unbookmarked
        if (pendingMove?.page != page) {
          screen.setPageReadingBookmarkSelected(bookmark.isPageReadingBookmark(page))
        }
      }
      .launchIn(scope)
  }

  fun unbind(screen: Screen) {
    if (this.screen === screen) {
      // Finish a pending move before clearing the screen so leaving the activity neither drops the
      // bookmark nor leaves its toast attached without a timeout.
      pendingMove?.page?.let(::confirmPendingMove)
      this.screen = null
      currentPage = null
      persistedBookmark = null
      currentJob?.cancel()
      currentJob = null
    }
  }

  fun togglePageReadingBookmark(page: Int) {
    val pending = pendingMove
    if (pending != null && pending.page == page) {
      // re-tapping the page we just optimistically (but not yet durably) bookmarked - same as
      // tapping Undo on its toast: cancel the pending write instead of touching the dao
      cancelPendingMove(page)
    } else if (persistedBookmark.isPageReadingBookmark(page)) {
      // un-bookmarking an already-persisted page - immediate, no undo affordance
      pending?.let { cancelPendingMove(it.page) }
      scope.launch {
        val isBookmarked = readingBookmarksDao.togglePageReadingBookmark(page)
        if (currentPage == page) {
          screen?.setPageReadingBookmarkSelected(isBookmarked)
        }
      }
    } else {
      // moving/making a bookmark
      val previousBookmark = pending?.previousBookmark ?: persistedBookmark
      pending?.timeoutJob?.cancel()

      val isEducation = quranSettings.markMovableBookmarkEducationSeen()

      if (currentPage == page) {
        screen?.setPageReadingBookmarkSelected(true)
      }

      val timeoutJob = appCoroutineScope.launch {
        delay(if (isEducation) educationTimeout else movedTimeout)
        // dismissReadingBookmarkMovedToast touches ui hierarchy so needs to be on main
        withContext(Dispatchers.Main) {
          confirmPendingMove(page)
        }
      }
      pendingMove = PendingMove(page, previousBookmark, timeoutJob)

      screen?.showReadingBookmarkMovedToast(
        previousBookmark = previousBookmark,
        isEducation = isEducation,
        onUndo = { cancelPendingMove(page) },
        onConfirm = { confirmPendingMove(page) }
      )
    }
  }

  private fun confirmPendingMove(movedToPage: Int) {
    takePendingMove(movedToPage) ?: return
    appCoroutineScope.launch { readingBookmarksDao.setPageReadingBookmark(movedToPage) }
    screen?.dismissReadingBookmarkMovedToast()
  }

  private fun cancelPendingMove(movedToPage: Int) {
    takePendingMove(movedToPage) ?: return
    val viewedPage = currentPage
    if (viewedPage != null) {
      screen?.setPageReadingBookmarkSelected(persistedBookmark.isPageReadingBookmark(viewedPage))
    }
    screen?.dismissReadingBookmarkMovedToast()
  }

  private fun takePendingMove(page: Int): PendingMove? {
    val pending = pendingMove?.takeIf { it.page == page } ?: return null
    pendingMove = null
    pending.timeoutJob.cancel()
    return pending
  }

  private fun ReadingBookmark?.isPageReadingBookmark(page: Int): Boolean {
    return this is PageReadingBookmark && this.page == page
  }

  private data class PendingMove(
    val page: Int,
    val previousBookmark: ReadingBookmark?,
    val timeoutJob: Job
  )

  interface Screen {
    fun setPageReadingBookmarkSelected(isBookmarked: Boolean)
    fun showReadingBookmarkMovedToast(
      previousBookmark: ReadingBookmark?,
      isEducation: Boolean,
      onUndo: () -> Unit,
      onConfirm: () -> Unit
    )
    fun dismissReadingBookmarkMovedToast()
  }

  companion object {
    val educationTimeout = 9.seconds
    val movedTimeout = 5.seconds
  }
}
