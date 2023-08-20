package com.quran.labs.androidquran.worker

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.core.worker.WorkerTaskFactory
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.database.AudioDatabaseVersionChecker
import com.quran.labs.androidquran.database.SuraTimingDatabaseHandler
import com.quran.labs.androidquran.feature.audio.AudioUpdater
import com.quran.labs.androidquran.feature.audio.api.AudioUpdateService
import com.quran.labs.androidquran.feature.audio.util.AudioFileCheckerImpl
import com.quran.labs.androidquran.feature.audio.util.MD5Calculator
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.NotificationChannelUtil
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class AudioUpdateWorker(
  private val context: Context,
  params: WorkerParameters,
  private val audioUpdateService: AudioUpdateService,
  private val audioUtils: AudioUtils,
  private val quranFileUtils: QuranFileUtils,
  private val quranSettings: QuranSettings
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result = coroutineScope {
    val audioPathRoot = quranFileUtils.getQuranAudioDirectory(context)
    if (audioPathRoot != null) {
      val currentVersion = quranSettings.currentAudioRevision
      val updates = audioUpdateService.getUpdates(currentVersion)

      Timber.d("local version: %d - server version: %d",
          currentVersion, updates.currentRevision)
      if (currentVersion != updates.currentRevision) {
        val localFilesToDelete = AudioUpdater.computeUpdates(
            updates.updates, audioUtils.getQariList(context),
            AudioFileCheckerImpl(MD5Calculator, audioPathRoot),
            AudioDatabaseVersionChecker()
        )

        Timber.d("update count: %d", localFilesToDelete.size)
        if (localFilesToDelete.isNotEmpty()) {
          localFilesToDelete.forEach { localUpdate ->
            if (localUpdate.needsDatabaseUpgrade) {
              // delete the database
              val dbPath = audioUtils.getQariDatabasePathIfGapless(localUpdate.qari)
              dbPath?.let { SuraTimingDatabaseHandler.clearDatabaseHandlerIfExists(it) }
              Timber.d("would remove %s", dbPath)
              File(dbPath).delete()
            }

            val qari = localUpdate.qari
            val path = audioUtils.getLocalQariUrl(qari)
            localUpdate.files.forEach {
              // delete the file
              val filePath = if (qari.isGapless) {
                path + File.separator + it
              } else {
                // this is a hack to drop the leading 0s in the file name
                val sura = it.substring(0, 3).toInt().toString()
                val ayah = it.substring(3, 6).toInt().toString()
                path + File.separator + sura + File.separator + ayah + ".mp3"
              }
              Timber.d("would remove %s", filePath)
              File(filePath).delete()
            }
          }

          // push a notification to inform the person that some files
          // have been deleted.
          sendNotification(context)
        }
        Timber.d("updating audio to revision: %d", updates.currentRevision)
        quranSettings.currentAudioRevision = updates.currentRevision
      }
    }
    Result.success()
  }

  private fun sendNotification(context: Context) {
    val notificationColor = ContextCompat.getColor(context, R.color.notification_color)
    val notification =
      NotificationCompat.Builder(context.applicationContext, Constants.DOWNLOAD_CHANNEL)
          .setSmallIcon(R.drawable.ic_notification)
          .setColor(notificationColor)
          .setContentTitle(context.getString(R.string.audio_updated_title))
          .setContentText(context.getString(R.string.audio_updated_text))
          .setStyle(
              NotificationCompat.BigTextStyle()
                  .bigText(context.getString(R.string.audio_updated_text))
          )
          .build()

    val notificationManager =
      context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
          as NotificationManager

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || notificationManager.areNotificationsEnabled()) {
      NotificationChannelUtil.setupNotificationChannel(
        notificationManager,
        Constants.DOWNLOAD_CHANNEL, context.getString(R.string.notification_channel_download)
      )
      notificationManager.notify(Constants.NOTIFICATION_ID_AUDIO_UPDATE, notification)
    }
  }

  class Factory @Inject constructor(
    private val audioUpdateService: AudioUpdateService,
    private val audioUtils: AudioUtils,
    private val quranFileUtils: QuranFileUtils,
    private val quranSettings: QuranSettings
  ) : WorkerTaskFactory {
    override fun makeWorker(
      appContext: Context,
      workerParameters: WorkerParameters
    ): ListenableWorker {
      return AudioUpdateWorker(
          appContext, workerParameters, audioUpdateService, audioUtils, quranFileUtils,
          quranSettings
      )
    }
  }
}
