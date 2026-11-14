package com.quran.mobile.feature.progress.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.progress.di.ProgressComponentInterface
import com.quran.mobile.progress.reminder.Reminder
import com.quran.mobile.progress.reminder.ReminderDecision

/** The daily reminder's check: says what's due, if anything, then waits for the next day's. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val component = (applicationContext as QuranApplicationComponentProvider)
      .provideQuranApplicationComponent() as ProgressComponentInterface
    val preferences = component.progressPreferences()
    val clock = component.progressClock()
    try {
      val snapshot = component.progressRepository().snapshot()
      val reminder = ReminderDecision.decide(
        enabled = preferences.reminder,
        nowMs = clock.nowMs(),
        zone = clock.zone(),
        reminderMinute = preferences.reminderMinute,
        lastReminderDay = preferences.lastReminderDay,
        readerOpen = component.readerPresence().open,
        bookmarks = snapshot.bookmarks,
      )
      if (reminder != Reminder.None && component.reminderNotifier().show(reminder)) {
        preferences.lastReminderDay = clock.today()
      }
    } finally {
      component.reminderScheduler().scheduleNext()
    }
    return Result.success()
  }
}
