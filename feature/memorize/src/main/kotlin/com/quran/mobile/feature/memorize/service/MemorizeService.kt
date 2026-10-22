package com.quran.mobile.feature.memorize.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.quran.data.model.SuraAyah
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.memorize.R
import com.quran.mobile.feature.memorize.di.MemorizeComponentInterface
import com.quran.mobile.feature.memorize.session.MemorizeSessionController
import com.quran.mobile.feature.memorize.session.MemorizeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Keeps a memorization session listening while the app isn't visible (e.g. the screen is off
 * during salah). Android only lets a foreground service with the microphone type keep using the
 * microphone in the background, and it must be started while the app is visible.
 *
 * The notification shows where the user is, never what was heard.
 */
class MemorizeService : Service() {

  private lateinit var controller: MemorizeSessionController
  private var scope: CoroutineScope? = null
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onCreate() {
    super.onCreate()
    val component = (application as QuranApplicationComponentProvider)
      .provideQuranApplicationComponent() as MemorizeComponentInterface
    controller = component.memorizeSessionController()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_PAUSE_RESUME -> controller.pauseOrResume()
      ACTION_STOP -> {
        controller.stop()
        stopSelf()
        return START_NOT_STICKY
      }
    }
    val state = controller.state.value
    ServiceCompat.startForeground(
      this, NOTIFICATION_ID, notification(state),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
    )
    if (scope == null) {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
      this.scope = scope
      controller.state
        // what the notification shows; not every recognized word
        .map { current -> current?.let { Shown(it.status, it.currentAyah) } }
        .distinctUntilChanged()
        .onEach { shown ->
          // it is only started once the session exists, so no session means it ended
          if (shown == null || shown.status == MemorizeState.Status.FINISHED) {
            releaseWakeLock()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return@onEach
          }
          controller.state.value?.let {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(it))
          }
          if (shown.status == MemorizeState.Status.LISTENING) holdWakeLock() else releaseWakeLock()
        }
        .launchIn(scope)
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    releaseWakeLock()
    scope?.cancel()
    scope = null
    super.onDestroy()
  }

  private data class Shown(val status: MemorizeState.Status, val ayah: SuraAyah)

  /**
   * Keeps the CPU running while listening with the screen off (e.g. during salah). Renewed whenever
   * the position changes; the timeout only guards against a session that is somehow left running.
   */
  private fun holdWakeLock() {
    val lock = wakeLock ?: getSystemService(PowerManager::class.java)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "quran:memorize")
      .apply { setReferenceCounted(false) }
      .also { wakeLock = it }
    lock.acquire(WAKE_LOCK_TIMEOUT_MS)
  }

  private fun releaseWakeLock() {
    wakeLock?.takeIf { it.isHeld }?.release()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun notification(state: MemorizeState?): Notification {
    createChannel()
    val listening = state?.status == MemorizeState.Status.LISTENING
    val ayah = state?.currentAyah?.let { "${it.sura}:${it.ayah}" }.orEmpty()
    // the user always knows when they are being recorded
    val position = if (state?.recordingId != null) getString(R.string.memorize_recording_notification, ayah) else ayah
    val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
      PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_memorize_notification)
      .setContentTitle(getString(if (listening) R.string.memorize_listening else R.string.memorize_paused))
      .setContentText(position)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .setContentIntent(openApp)
      .addAction(
        0, getString(if (listening) R.string.memorize_pause else R.string.memorize_resume),
        action(ACTION_PAUSE_RESUME, 1)
      )
      .addAction(0, getString(R.string.memorize_stop), action(ACTION_STOP, 2))
      .build()
  }

  private fun action(action: String, requestCode: Int): PendingIntent =
    PendingIntent.getService(
      this, requestCode, Intent(this, MemorizeService::class.java).setAction(action),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

  private fun createChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(NotificationManager::class.java)
      if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(
          NotificationChannel(CHANNEL_ID, getString(R.string.memorize_title), NotificationManager.IMPORTANCE_LOW)
        )
      }
    }
  }

  companion object {
    private const val CHANNEL_ID = "memorize_session"
    private const val NOTIFICATION_ID = 7_412
    private const val ACTION_PAUSE_RESUME = "com.quran.mobile.feature.memorize.PAUSE_RESUME"
    private const val ACTION_STOP = "com.quran.mobile.feature.memorize.STOP"
    private const val WAKE_LOCK_TIMEOUT_MS = 3 * 60 * 60 * 1000L

    /** Call once the session exists, while the app is in the foreground. */
    fun start(context: Context) {
      try {
        ContextCompat.startForegroundService(context, Intent(context, MemorizeService::class.java))
      } catch (_: IllegalStateException) {
        // not allowed from the background; the session then only listens while the app is visible
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, MemorizeService::class.java))
    }
  }
}
