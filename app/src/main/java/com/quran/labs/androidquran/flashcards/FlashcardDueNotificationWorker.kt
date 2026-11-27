package com.quran.labs.androidquran.flashcards

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.quran.labs.androidquran.R
import java.util.Locale
import java.util.concurrent.TimeUnit

class FlashcardDueNotificationWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    FlashcardDueNotificationScheduler.checkAndSendDueNotification(applicationContext)
    FlashcardDueNotificationScheduler.scheduleNextDueNotification(applicationContext)
    return Result.success()
  }
}

class FlashcardDueBroadcastReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val appContext = context.applicationContext
    FlashcardDueNotificationScheduler.ensureNotificationChannel(appContext)
    FlashcardDueNotificationScheduler.checkAndSendDueNotification(appContext)
    FlashcardDueNotificationScheduler.scheduleNextDueNotification(appContext)
  }
}

object FlashcardDueNotificationScheduler {

  const val CHANNEL_ID = "quran_memory_flashcards_popup_v2"
  const val NOTIFICATION_ID = 4412
  const val ACTION_FLASHCARD_DUE_ALARM = "com.quran.labs.androidquran.ACTION_FLASHCARD_DUE_ALARM"
  private const val UNIQUE_ONE_TIME_WORK_NAME = "quran_memory_flashcard_due_work"
  private const val UNIQUE_PERIODIC_WORK_NAME = "quran_memory_flashcard_periodic_work"
  private const val ALARM_REQUEST_CODE = 4412

  fun initialize(context: Context) {
    ensureNotificationChannel(context)
    scheduleNextDueNotification(context)
  }

  private fun getLocalizedContext(context: Context): Context {
    val isArabic = MemoryFlashcardManager.isAppInArabicMode(context)
    val targetLocale = if (isArabic) Locale("ar") else Locale.ENGLISH
    val config = Configuration(context.resources.configuration)
    config.setLocale(targetLocale)
    config.setLayoutDirection(targetLocale)
    return context.createConfigurationContext(config)
  }

  fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return
      val localizedContext = getLocalizedContext(context)
      val existing = manager.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_NOTIFICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()

        val channel = NotificationChannel(
          CHANNEL_ID,
          localizedContext.getString(R.string.notification_channel_flashcards),
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = localizedContext.getString(R.string.flashcard_notification_channel_desc)
          enableVibration(true)
          vibrationPattern = longArrayOf(0L, 300L, 150L, 300L)
          enableLights(true)
          lockscreenVisibility = Notification.VISIBILITY_PUBLIC
          if (soundUri != null) {
            setSound(soundUri, audioAttributes)
          }
        }
        manager.createNotificationChannel(channel)
      } else {
        existing.name = localizedContext.getString(R.string.notification_channel_flashcards)
        existing.description = localizedContext.getString(R.string.flashcard_notification_channel_desc)
        manager.createNotificationChannel(existing)
      }
    }
  }

  fun scheduleNextDueNotification(context: Context) {
    val allCards = MemoryFlashcardManager.getAllCards(context)
    val now = System.currentTimeMillis()
    if (allCards.isEmpty()) {
      cancelScheduledNotifications(context)
      return
    }

    ensureNotificationChannel(context)

    // Find the earliest upcoming review timestamp.
    // If there are due cards that have not yet been notified for their current due cycle,
    // schedule within 15 seconds; otherwise schedule at the earliest future nextReviewTimeMillis
    // (or in 20 minutes for newly created cards).
    val nextTriggerTime = allCards
      .map { card ->
        when {
          card.nextReviewTimeMillis > now -> card.nextReviewTimeMillis
          card.lastNotifiedAtReviewTimeMillis < card.nextReviewTimeMillis ->
            now + MemoryFlashcardManager.SCHEDULE_INTERVALS_MS.first()
          else -> now + MemoryFlashcardManager.SCHEDULE_INTERVALS_MS.first()
        }
      }
      .minOrNull() ?: return

    val delayMs = (nextTriggerTime - now).coerceAtLeast(5_000L)

    // 1. Schedule OneTimeWorkRequest + PeriodicWorkRequest via WorkManager so dormant/closed app checks run
    try {
      val workManager = WorkManager.getInstance(context)
      val oneTimeRequest = OneTimeWorkRequestBuilder<FlashcardDueNotificationWorker>()
        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        .build()
      workManager.enqueueUniqueWork(
        UNIQUE_ONE_TIME_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        oneTimeRequest
      )

      val periodicRequest = PeriodicWorkRequestBuilder<FlashcardDueNotificationWorker>(
        15,
        TimeUnit.MINUTES
      ).build()
      workManager.enqueueUniquePeriodicWork(
        UNIQUE_PERIODIC_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        periodicRequest
      )
    } catch (_: Exception) {
      // WorkManager may not be initialized in unit test environments
    }

    // 2. Schedule RTC_WAKEUP alarm via AlarmManager.setAndAllowWhileIdle so the popup fires even in Doze mode
    try {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
      if (alarmManager != null) {
        val intent = Intent(context, FlashcardDueBroadcastReceiver::class.java).apply {
          action = ACTION_FLASHCARD_DUE_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
          context,
          ALARM_REQUEST_CODE,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          now + delayMs,
          pendingIntent
        )
      }
    } catch (_: Exception) {
      // Ignore in unit test environments
    }
  }

  fun cancelScheduledNotifications(context: Context) {
    try {
      val workManager = WorkManager.getInstance(context)
      workManager.cancelUniqueWork(UNIQUE_ONE_TIME_WORK_NAME)
      workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
    } catch (_: Exception) {
    }
    try {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
      val intent = Intent(context, FlashcardDueBroadcastReceiver::class.java).apply {
        action = ACTION_FLASHCARD_DUE_ALARM
      }
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
      alarmManager?.cancel(pendingIntent)
    } catch (_: Exception) {
    }
  }

  fun checkAndSendDueNotification(context: Context): Boolean {
    val dueCards = MemoryFlashcardManager.getDueCards(context)
    if (dueCards.isEmpty()) return false

    ensureNotificationChannel(context)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) return false
    }

    val isArabic = MemoryFlashcardManager.isAppInArabicMode(context)
    val localizedContext = getLocalizedContext(context)
    val firstCard = dueCards.first()
    val launchIntent = Intent(context, MemoryFlashcardsActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      NOTIFICATION_ID,
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val formattedCount = MemoryFlashcardManager.formatNumber(dueCards.size, isArabic)
    val title = localizedContext.getString(R.string.flashcard_due_notification_title, formattedCount)
    val body = localizedContext.getString(
      R.string.flashcard_due_notification_body,
      firstCard.firstAyahCue,
      firstCard.lastAyahCue
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_memorization_mode)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setFullScreenIntent(pendingIntent, true)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)
      .build()

    return try {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
      MemoryFlashcardManager.markDueCardsNotified(context, dueCards)
      true
    } catch (_: SecurityException) {
      false
    }
  }
}
