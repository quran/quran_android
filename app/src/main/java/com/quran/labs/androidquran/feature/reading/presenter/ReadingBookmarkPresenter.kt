package com.quran.labs.androidquran.feature.reading.presenter

import com.quran.data.core.ReadingBookmarkUpdater
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.EmptyReadingBookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.bookmark.isAt
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.ContributesBinding
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

@ContributesBinding(AppScope::class)
class ReadingBookmarkPresenter @Inject constructor(
  private val readingBookmarksDao: ReadingBookmarksDao,
  private val quranSettings: QuranSettings,
  private val appCoroutineScope: AppCoroutineScope
) : ReadingBookmarkUpdater {
  private val scope = MainScope()
  private var currentJob: Job? = null
  private var readingBookmarks: List<ReadingBookmark> = emptyList()
  private var toastTimeoutJob: Job? = null
  private var screen: Screen? = null

  fun bind(pageFlow: Flow<Int>, screen: Screen) {
    currentJob?.cancel()
    this.screen = screen
    currentJob = combine(
      pageFlow,
      readingBookmarksDao.readingBookmarksFlow()
    ) { page, bookmarks -> page to bookmarks }
      .onEach { (page, bookmarks) ->
        readingBookmarks = bookmarks
        // any of the three pins sitting on this page fills the action bar icon
        screen.setPageReadingBookmarkSelected(
          bookmarks.any { it.isAt(ReadingBookmarkTarget.Page(page)) }
        )
      }
      .launchIn(scope)
  }

  fun unbind(screen: Screen) {
    if (this.screen === screen) {
      this.screen = null
      readingBookmarks = emptyList()
      toastTimeoutJob?.cancel()
      toastTimeoutJob = null
      currentJob?.cancel()
      currentJob = null
    }
  }

  fun readingBookmarkFor(slot: ReadingBookmarkType): ReadingBookmark? =
    readingBookmarks.firstOrNull { it.slot == slot && it !is EmptyReadingBookmark }

  override fun placeReadingBookmark(slot: ReadingBookmarkType, target: ReadingBookmarkTarget) {
    val previous = readingBookmarkFor(slot)
    if (previous?.isAt(target) != true) {
      appCoroutineScope.launch {
        when (target) {
          is ReadingBookmarkTarget.Page -> readingBookmarksDao.setPageReadingBookmark(
            slot,
            target.page
          )

          is ReadingBookmarkTarget.Ayah ->
            readingBookmarksDao.setAyahReadingBookmark(slot, target.suraAyah)
        }
      }

      showToast(
        change = ReadingBookmarkChange.Placed(slot, target, previous),
        isEducation = quranSettings.markMovableBookmarkEducationSeen()
      )
    }
  }

  override fun clearReadingBookmark(slot: ReadingBookmarkType) {
    val previous = readingBookmarkFor(slot) ?: return

    appCoroutineScope.launch { readingBookmarksDao.clearReadingBookmark(slot) }
    showToast(change = ReadingBookmarkChange.Cleared(slot, previous), isEducation = false)
  }

  private fun showToast(change: ReadingBookmarkChange, isEducation: Boolean) {
    toastTimeoutJob?.cancel()
    toastTimeoutJob = appCoroutineScope.launch {
      delay(if (isEducation) educationTimeout else changeTimeout)
      withContext(Dispatchers.Main) { dismissToast() }
    }

    screen?.showReadingBookmarkChangedToast(
      change = change,
      isEducation = isEducation,
      onUndo = { undo(change) },
      onDismiss = { dismissToast() }
    )
  }

  private fun undo(change: ReadingBookmarkChange) {
    dismissToast()
    appCoroutineScope.launch {
      val slot = change.slot
      val current = readingBookmarksDao.readingBookmarks()
        .firstOrNull { it.slot == slot && it !is EmptyReadingBookmark }

      val isStillOurs = when (change) {
        is ReadingBookmarkChange.Placed -> current?.isAt(change.target) == true
        is ReadingBookmarkChange.Cleared -> current == null
      }

      if (isStillOurs) {
        when (val previous = change.previous) {
          null -> readingBookmarksDao.clearReadingBookmark(slot)
          is PageReadingBookmark -> readingBookmarksDao.setPageReadingBookmark(slot, previous.page)
          is AyahReadingBookmark ->
            readingBookmarksDao.setAyahReadingBookmark(slot, previous.asSuraAyah())

          is EmptyReadingBookmark -> readingBookmarksDao.clearReadingBookmark(slot)
        }
      }
    }
  }

  private fun dismissToast() {
    toastTimeoutJob?.cancel()
    toastTimeoutJob = null
    screen?.dismissReadingBookmarkChangedToast()
  }

  interface Screen {
    fun setPageReadingBookmarkSelected(isBookmarked: Boolean)
    fun showReadingBookmarkChangedToast(
      change: ReadingBookmarkChange,
      isEducation: Boolean,
      onUndo: () -> Unit,
      onDismiss: () -> Unit
    )
    fun dismissReadingBookmarkChangedToast()
  }

  companion object {
    val educationTimeout = 9.seconds
    val changeTimeout = 5.seconds
  }
}

sealed interface ReadingBookmarkChange {
  val slot: ReadingBookmarkType
  val previous: ReadingBookmark?

  data class Placed(
    override val slot: ReadingBookmarkType,
    val target: ReadingBookmarkTarget,
    override val previous: ReadingBookmark?
  ) : ReadingBookmarkChange

  data class Cleared(
    override val slot: ReadingBookmarkType,
    override val previous: ReadingBookmark
  ) : ReadingBookmarkChange
}
