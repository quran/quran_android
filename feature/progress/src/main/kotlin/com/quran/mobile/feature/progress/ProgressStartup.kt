package com.quran.mobile.feature.progress

import com.quran.data.di.AppScope
import com.quran.mobile.feature.progress.reminder.ReminderScheduler
import com.quran.mobile.progress.log.BookmarkMoveRecorder
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** What Progress does when the app starts: keep each bookmark's moves, and the daily reminder's check. */
@SingleIn(AppScope::class)
class ProgressStartup @Inject constructor(
  private val recorder: BookmarkMoveRecorder,
  private val reminderScheduler: ReminderScheduler,
) {
  /** Call once WorkManager is initialized. */
  fun start() {
    recorder.start()
    reminderScheduler.ensure()
  }
}
