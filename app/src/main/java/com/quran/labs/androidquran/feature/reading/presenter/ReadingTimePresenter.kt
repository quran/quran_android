package com.quran.labs.androidquran.feature.reading.presenter

import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.di.ActivityScope
import com.quran.mobile.progress.reading.ReaderPresence
import com.quran.mobile.progress.reading.ReadingTimeAccumulator
import com.quran.mobile.progress.reading.ReadingTimeLog
import com.quran.mobile.progress.time.ProgressClock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps how long Quran pages are open in the reader, for Progress (see [ReadingTimeAccumulator]):
 * while the reader is resumed, by the page or pages on screen, saved every minute and when it's left.
 */
@ActivityScope
class ReadingTimePresenter @Inject constructor(
  private val readingTimeLog: ReadingTimeLog,
  private val readerPresence: ReaderPresence,
  private val clock: ProgressClock,
  @param:Named(DependencyInjectionConstants.CURRENT_PAGE_TYPE) private val pageType: String,
) {
  private val scope = MainScope()
  private val accumulator = ReadingTimeAccumulator(zone = { clock.zone() })
  private var pagesJob: Job? = null
  private var tickJob: Job? = null
  private var lastSavedAt = 0L

  /** Starts counting the pages [pageFlow] shows; [visiblePages] gives all those on screen with one. */
  fun bind(pageFlow: Flow<Int>, visiblePages: (Int) -> Set<Int>) {
    cancelJobs()
    readerPresence.open = true
    lastSavedAt = clock.nowMs()
    var resumed = false
    pagesJob = pageFlow
      .onEach { page ->
        val now = clock.nowMs()
        if (resumed) {
          accumulator.onPages(now, visiblePages(page))
        } else {
          accumulator.onResume(now, visiblePages(page))
          resumed = true
        }
      }
      .launchIn(scope)
    tickJob = scope.launch {
      while (isActive) {
        delay(TICK_MS)
        val now = clock.nowMs()
        accumulator.tick(now)
        if (now - lastSavedAt >= SAVE_MS) save(now)
      }
    }
  }

  /** The user touched the reader (e.g. scrolling a translation): they're still reading. */
  fun onInteraction() {
    if (pagesJob != null) accumulator.onInteraction(clock.nowMs())
  }

  fun unbind() {
    if (pagesJob == null) return
    cancelJobs()
    val now = clock.nowMs()
    accumulator.onPause(now)
    readerPresence.open = false
    save(now)
  }

  private fun cancelJobs() {
    pagesJob?.cancel()
    tickJob?.cancel()
    pagesJob = null
    tickJob = null
  }

  private fun save(now: Long) {
    lastSavedAt = now
    val times = accumulator.drain()
    if (times.isEmpty()) return
    scope.launch(NonCancellable) { readingTimeLog.add(times, pageType, now) }
  }

  private companion object {
    const val TICK_MS = 30_000L
    const val SAVE_MS = 60_000L
  }
}
