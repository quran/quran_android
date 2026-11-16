package com.quran.mobile.feature.progress.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.progress.ProgressActivity
import com.quran.mobile.feature.progress.R
import com.quran.mobile.feature.progress.ui.dayOnly
import com.quran.mobile.progress.reminder.Reminder
import com.quran.mobile.progress.summary.BookmarkProgress
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Shows the daily reading reminder; a tap opens the bookmark in Progress. */
@SingleIn(AppScope::class)
class ReminderNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {

  /** Whether notifications may be shown (on Android 13 and later the user has to allow them). */
  fun allowed(): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  /** Shows [reminder]; false if notifications aren't allowed. */
  fun show(reminder: Reminder): Boolean {
    if (!allowed()) return false
    val bookmark: BookmarkProgress
    val text = when (reminder) {
      is Reminder.NotReadToday -> {
        bookmark = reminder.bookmark
        context.getString(R.string.progress_reminder_not_read, reminder.perDay, context.dayOnly(reminder.finishByDay))
      }
      is Reminder.Behind -> {
        bookmark = reminder.bookmark
        context.getString(R.string.progress_reminder_behind, reminder.pages, reminder.perDay, context.dayOnly(reminder.finishByDay))
      }
      is Reminder.Overdue -> {
        bookmark = reminder.bookmark
        context.getString(R.string.progress_reminder_overdue, reminder.remaining)
      }
      Reminder.None -> return false
    }
    createChannel()
    val open = PendingIntent.getActivity(
      context,
      bookmark.slot.ordinal,
      ProgressActivity.intent(context, bookmark.slot),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val title = ReadingBookmarkSlots.displayName(context, bookmark.slot, bookmark.name)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_progress_notification)
      .setContentTitle(context.getString(R.string.progress_reminder_title, title))
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setContentIntent(open)
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()
    return try {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
      true
    } catch (_: SecurityException) {
      false
    }
  }

  private fun createChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.progress_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
      .apply { description = context.getString(R.string.progress_reminder_channel_description) }
    manager.createNotificationChannel(channel)
  }

  private companion object {
    const val CHANNEL_ID = "progress_reminder"
    // the app's own notifications use 1 to 5, Memorize's its own range
    const val NOTIFICATION_ID = 4_210
  }
}
