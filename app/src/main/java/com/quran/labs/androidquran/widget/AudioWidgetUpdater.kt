package com.quran.labs.androidquran.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.service.AudioService
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Renders [AudioWidget]s and keeps track of which widget (if any) owns the current
 * playback in [AudioService].
 */
@SingleIn(AppScope::class)
class AudioWidgetUpdater @Inject constructor(
  @ApplicationContext private val appContext: Context,
  private val audioWidgetPreferences: AudioWidgetPreferences,
  private val quranDisplayData: QuranDisplayData,
  private val qariUtil: QariUtil
) {

  private data class WidgetPlayback(
    val widgetId: Int,
    val ayah: SuraAyah,
    val playbackStatus: PlaybackStatus
  )

  @Volatile
  private var widgetPlayback: WidgetPlayback? = null

  // a one off message to show on a widget instead of its idle status
  @Volatile
  private var widgetMessage: Pair<Int, Int>? = null

  /**
   * Called by [AudioService] whenever its playback status changes.
   * @param widgetId the widget that started the current playback, or null if the
   * current playback was not started from a widget.
   */
  fun onPlaybackStatus(widgetId: Int?, audioStatus: AudioStatus) {
    val updated = if (widgetId != null && audioStatus is AudioStatus.Playback) {
      WidgetPlayback(widgetId, audioStatus.currentAyah, audioStatus.playbackStatus)
    } else {
      null
    }

    val previous = widgetPlayback
    if (updated == previous) {
      return
    }

    widgetPlayback = updated
    if (updated != null && widgetMessage?.first == updated.widgetId) {
      widgetMessage = null
    }
    setOfNotNull(previous?.widgetId, updated?.widgetId).forEach { updateWidget(it) }
  }

  fun showMessage(widgetId: Int, @StringRes message: Int) {
    widgetMessage = widgetId to message
    updateWidget(widgetId)
  }

  fun updateWidget(widgetId: Int) {
    AppWidgetManager.getInstance(appContext).updateAppWidget(widgetId, remoteViews(widgetId))
  }

  fun updateAllWidgets() {
    val appWidgetManager = AppWidgetManager.getInstance(appContext)
    appWidgetManager.getAppWidgetIds(ComponentName(appContext, AudioWidget::class.java))
      .forEach { appWidgetManager.updateAppWidget(it, remoteViews(it)) }
  }

  fun onWidgetDeleted(widgetId: Int) {
    audioWidgetPreferences.delete(widgetId)
    if (widgetMessage?.first == widgetId) {
      widgetMessage = null
    }
  }

  private fun remoteViews(widgetId: Int): RemoteViews {
    val views = RemoteViews(appContext.packageName, R.layout.audio_widget)
    views.setOnClickPendingIntent(R.id.audio_widget_open_app, openAppPendingIntent())
    views.setOnClickPendingIntent(R.id.audio_widget_info, configurePendingIntent(widgetId))

    val config = audioWidgetPreferences.config(widgetId)
    if (config == null) {
      // the widget was placed without completing its configuration
      views.setTextViewText(R.id.audio_widget_title, appContext.getString(R.string.audio_widget_name))
      views.setTextViewText(R.id.audio_widget_qari, appContext.getString(R.string.audio_widget_not_configured))
      views.setViewVisibility(R.id.audio_widget_status, View.GONE)
      views.setViewVisibility(R.id.audio_widget_controls, View.GONE)
      return views
    }

    val playback = widgetPlayback?.takeIf { it.widgetId == widgetId }
    val isPlaying = playback != null && playback.playbackStatus != PlaybackStatus.PAUSED

    views.setTextViewText(R.id.audio_widget_title, title(config))
    views.setTextViewText(
      R.id.audio_widget_qari,
      qariUtil.getQariList(appContext).firstOrNull { it.id == config.qariId }?.name ?: ""
    )
    views.setViewVisibility(R.id.audio_widget_status, View.VISIBLE)
    views.setViewVisibility(R.id.audio_widget_controls, View.VISIBLE)
    views.setTextViewText(R.id.audio_widget_status, status(widgetId, config, playback))

    if (isPlaying) {
      views.setImageViewResource(R.id.audio_widget_play_pause, R.drawable.ic_pause)
      views.setContentDescription(R.id.audio_widget_play_pause, appContext.getString(R.string.pause))
      views.setOnClickPendingIntent(
        R.id.audio_widget_play_pause, servicePendingIntent(AudioService.ACTION_PAUSE)
      )
    } else {
      views.setImageViewResource(
        R.id.audio_widget_play_pause, com.quran.labs.androidquran.common.toolbar.R.drawable.ic_play
      )
      views.setContentDescription(R.id.audio_widget_play_pause, appContext.getString(R.string.play))
      views.setOnClickPendingIntent(R.id.audio_widget_play_pause, playPendingIntent(widgetId))
    }

    // previous and next only make sense for the widget that is currently playing
    val skipAlpha = if (playback != null) ENABLED_ALPHA else DISABLED_ALPHA
    views.setInt(R.id.audio_widget_previous, "setImageAlpha", skipAlpha)
    views.setInt(R.id.audio_widget_next, "setImageAlpha", skipAlpha)
    views.setOnClickPendingIntent(
      R.id.audio_widget_previous,
      if (playback != null) servicePendingIntent(AudioService.ACTION_REWIND) else null
    )
    views.setOnClickPendingIntent(
      R.id.audio_widget_next,
      if (playback != null) servicePendingIntent(AudioService.ACTION_SKIP) else null
    )
    return views
  }

  private fun title(config: AudioWidgetConfig): String {
    val customTitle = config.title
    return if (!customTitle.isNullOrBlank()) {
      customTitle
    } else if (config.start.sura == config.end.sura) {
      quranDisplayData.getSuraName(appContext, config.start.sura, true)
    } else {
      appContext.getString(
        R.string.audio_widget_range,
        quranDisplayData.getSuraName(appContext, config.start.sura, false),
        quranDisplayData.getSuraName(appContext, config.end.sura, false)
      )
    }
  }

  private fun status(
    widgetId: Int,
    config: AudioWidgetConfig,
    playback: WidgetPlayback?
  ): String {
    val message = widgetMessage?.takeIf { it.first == widgetId }?.second
    return when {
      playback != null -> {
        val ayah = quranDisplayData.getSuraAyahString(
          appContext, playback.ayah.sura, playback.ayah.ayah
        )
        val format = when (playback.playbackStatus) {
          PlaybackStatus.PREPARING -> R.string.audio_widget_loading
          PlaybackStatus.PLAYING -> R.string.audio_widget_playing
          PlaybackStatus.PAUSED -> R.string.audio_widget_paused
        }
        appContext.getString(format, ayah)
      }
      message != null -> appContext.getString(message)
      else -> appContext.getString(
        R.string.audio_widget_range,
        appContext.getString(R.string.sura_ayah, config.start.sura, config.start.ayah),
        appContext.getString(R.string.sura_ayah, config.end.sura, config.end.ayah)
      )
    }
  }

  private fun openAppPendingIntent(): PendingIntent {
    // the same intent as the launcher icon, so an existing task is brought to the front
    val intent = Intent(appContext, QuranDataActivity::class.java).apply {
      action = Intent.ACTION_MAIN
      addCategory(Intent.CATEGORY_LAUNCHER)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return PendingIntent.getActivity(
      appContext, REQUEST_CODE_OPEN_APP, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun configurePendingIntent(widgetId: Int): PendingIntent {
    val intent = Intent(appContext, AudioWidgetConfigureActivity::class.java).apply {
      action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
      putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
    return PendingIntent.getActivity(
      appContext, widgetId, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun playPendingIntent(widgetId: Int): PendingIntent {
    val intent = Intent(appContext, AudioService::class.java).apply {
      action = AudioService.ACTION_WIDGET_PLAYBACK
      putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      PendingIntent.getForegroundService(appContext, widgetId, intent, flags)
    } else {
      PendingIntent.getService(appContext, widgetId, intent, flags)
    }
  }

  private fun servicePendingIntent(action: String): PendingIntent {
    val intent = Intent(appContext, AudioService::class.java).setAction(action)
    return PendingIntent.getService(
      appContext, REQUEST_CODE_CONTROLS, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  companion object {
    // controls are shared across widgets, so they get their own request code
    private const val REQUEST_CODE_CONTROLS = -1
    private const val REQUEST_CODE_OPEN_APP = -2
    private const val ENABLED_ALPHA = 255
    private const val DISABLED_ALPHA = 97
  }
}
