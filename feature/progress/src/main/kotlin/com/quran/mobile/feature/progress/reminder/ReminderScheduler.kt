package com.quran.mobile.feature.progress.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.progress.ProgressPreferences
import com.quran.mobile.progress.reminder.ReminderDecision
import com.quran.mobile.progress.time.ProgressClock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.TimeUnit

/**
 * Keeps one check of the daily reminder waiting, at the time the user chose (WorkManager, so it
 * survives restarts). Each check schedules the next.
 */
@SingleIn(AppScope::class)
class ReminderScheduler @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val preferences: ProgressPreferences,
  private val clock: ProgressClock,
) {
  /** At app start: leaves a waiting check as it is. */
  fun ensure() = schedule(ExistingWorkPolicy.KEEP)

  /** After the user changed the reminder: the waiting check moves to the new time. */
  fun reschedule() = schedule(ExistingWorkPolicy.REPLACE)

  /** From the check itself, for the next day's (replacing it would cancel the check that's running). */
  fun scheduleNext() = schedule(ExistingWorkPolicy.APPEND_OR_REPLACE)

  private fun schedule(policy: ExistingWorkPolicy) {
    val workManager = WorkManager.getInstance(context)
    if (!preferences.reminder) {
      workManager.cancelUniqueWork(WORK_NAME)
      return
    }
    val now = clock.nowMs()
    val at = ReminderDecision.nextReminderAt(now, clock.zone(), preferences.reminderMinute)
    val request = OneTimeWorkRequestBuilder<ReminderWorker>()
      .setInitialDelay((at - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
      .build()
    workManager.enqueueUniqueWork(WORK_NAME, policy, request)
  }

  private companion object {
    const val WORK_NAME = "progress_reminder"
  }
}
