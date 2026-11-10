package com.quran.labs.androidquran.widget

import android.content.Context
import androidx.core.content.edit
import com.quran.data.model.SuraAyah
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject

/**
 * Playback settings for a single audio widget instance.
 *
 * [verseRepeat] and [rangeRepeat] follow [com.quran.labs.androidquran.common.audio.model.playback.AudioRequest]
 * semantics - 0 plays once, -1 repeats forever.
 */
data class AudioWidgetConfig(
  val title: String?,
  val qariId: Int,
  val start: SuraAyah,
  val end: SuraAyah,
  val verseRepeat: Int,
  val rangeRepeat: Int,
  val playbackSpeed: Float,
  val enforceBounds: Boolean
)

/**
 * Stores an [AudioWidgetConfig] per appWidgetId, so that each widget on the
 * home screen keeps its own isolated settings.
 */
class AudioWidgetPreferences @Inject constructor(
  @ApplicationContext appContext: Context
) {
  private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun config(widgetId: Int): AudioWidgetConfig? {
    if (!prefs.contains(key(widgetId, QARI))) {
      return null
    }

    return AudioWidgetConfig(
      title = prefs.getString(key(widgetId, TITLE), null),
      qariId = prefs.getInt(key(widgetId, QARI), 0),
      start = SuraAyah(
        prefs.getInt(key(widgetId, START_SURA), 1),
        prefs.getInt(key(widgetId, START_AYAH), 1)
      ),
      end = SuraAyah(
        prefs.getInt(key(widgetId, END_SURA), 1),
        prefs.getInt(key(widgetId, END_AYAH), 7)
      ),
      verseRepeat = prefs.getInt(key(widgetId, VERSE_REPEAT), 0),
      rangeRepeat = prefs.getInt(key(widgetId, RANGE_REPEAT), 0),
      playbackSpeed = prefs.getFloat(key(widgetId, SPEED), 1.0f),
      enforceBounds = prefs.getBoolean(key(widgetId, ENFORCE_BOUNDS), true)
    )
  }

  fun save(widgetId: Int, config: AudioWidgetConfig) {
    prefs.edit {
      putString(key(widgetId, TITLE), config.title)
      putInt(key(widgetId, QARI), config.qariId)
      putInt(key(widgetId, START_SURA), config.start.sura)
      putInt(key(widgetId, START_AYAH), config.start.ayah)
      putInt(key(widgetId, END_SURA), config.end.sura)
      putInt(key(widgetId, END_AYAH), config.end.ayah)
      putInt(key(widgetId, VERSE_REPEAT), config.verseRepeat)
      putInt(key(widgetId, RANGE_REPEAT), config.rangeRepeat)
      putFloat(key(widgetId, SPEED), config.playbackSpeed)
      putBoolean(key(widgetId, ENFORCE_BOUNDS), config.enforceBounds)
    }
  }

  fun delete(widgetId: Int) {
    val prefix = "$widgetId$SEPARATOR"
    prefs.edit {
      prefs.all.keys
        .filter { it.startsWith(prefix) }
        .forEach { remove(it) }
    }
  }

  private fun key(widgetId: Int, name: String) = "$widgetId$SEPARATOR$name"

  companion object {
    private const val PREFS_NAME = "audio_widget_prefs"
    private const val SEPARATOR = "_"

    private const val TITLE = "title"
    private const val QARI = "qari"
    private const val START_SURA = "start_sura"
    private const val START_AYAH = "start_ayah"
    private const val END_SURA = "end_sura"
    private const val END_AYAH = "end_ayah"
    private const val VERSE_REPEAT = "verse_repeat"
    private const val RANGE_REPEAT = "range_repeat"
    private const val SPEED = "speed"
    private const val ENFORCE_BOUNDS = "enforce_bounds"
  }
}
