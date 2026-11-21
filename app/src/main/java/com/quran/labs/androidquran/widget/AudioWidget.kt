package com.quran.labs.androidquran.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.quran.labs.androidquran.QuranApplication
import dev.zacsweers.metro.Inject

/**
 * Home screen widget that plays a configured range of verses, independently of
 * whatever the user is currently reading in the app.
 *
 * Each widget instance has its own settings, set via [AudioWidgetConfigureActivity].
 */
class AudioWidget : AppWidgetProvider() {

  @Inject
  lateinit var audioWidgetUpdater: AudioWidgetUpdater

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    inject(context)
    appWidgetIds.forEach { audioWidgetUpdater.updateWidget(it) }
    super.onUpdate(context, appWidgetManager, appWidgetIds)
  }

  override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    inject(context)
    appWidgetIds.forEach { audioWidgetUpdater.onWidgetDeleted(it) }
    super.onDeleted(context, appWidgetIds)
  }

  private fun inject(context: Context) {
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
  }
}
