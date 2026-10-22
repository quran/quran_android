package com.quran.mobile.feature.memorize.settings

import android.content.Context
import androidx.core.content.edit
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.memorize.recording.RecordingSplit
import com.quran.mobile.feature.memorize.tracker.hint.HintConfig
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Settings of the memorize feature, kept in their own preferences file. */
@SingleIn(AppScope::class)
class MemorizePreferences @Inject constructor(@param:ApplicationContext context: Context) {
  private val prefs = context.getSharedPreferences("memorize", Context.MODE_PRIVATE)

  /** The prayer corrector (مصحح الصلاة): give the next word when the user is stuck. */
  var correctorEnabled: Boolean
    get() = prefs.getBoolean(KEY_CORRECTOR, true)
    set(value) = prefs.edit { putBoolean(KEY_CORRECTOR, value) }

  var hintDelaySeconds: Int
    get() = prefs.getInt(KEY_DELAY, 4)
    set(value) = prefs.edit { putInt(KEY_DELAY, value.coerceIn(2, 15)) }

  var wordsPerHint: Int
    get() = prefs.getInt(KEY_WORDS, 1)
    set(value) = prefs.edit { putInt(KEY_WORDS, value.coerceIn(1, 5)) }

  var hintOnMistakes: Boolean
    get() = prefs.getBoolean(KEY_ON_MISTAKES, true)
    set(value) = prefs.edit { putBoolean(KEY_ON_MISTAKES, value) }

  var hintOnPauses: Boolean
    get() = prefs.getBoolean(KEY_ON_PAUSES, true)
    set(value) = prefs.edit { putBoolean(KEY_ON_PAUSES, value) }

  /** Treat wrong short vowels (tashkeel) as mistakes. Off by default: fewer false alarms. */
  var strictTashkeel: Boolean
    get() = prefs.getBoolean(KEY_STRICT, false)
    set(value) = prefs.edit { putBoolean(KEY_STRICT, value) }

  /** The reciter whose voice gives the hints (qari id). Husary Murattal by default. */
  var promptQariId: Int
    get() = prefs.getInt(KEY_PROMPT_QARI, 1)
    set(value) = prefs.edit { putInt(KEY_PROMPT_QARI, value) }

  /** Download the speech model and the hint voice as needed, without asking during a session. */
  var autoDownload: Boolean
    get() = prefs.getBoolean(KEY_AUTO_DOWNLOAD, false)
    set(value) = prefs.edit { putBoolean(KEY_AUTO_DOWNLOAD, value) }

  enum class RecordingMode { OFF, ALWAYS, ASK }

  /** Whether sessions are recorded so the user can listen to themselves. Off by default. */
  var recordingMode: RecordingMode
    get() = prefs.getString(KEY_RECORDING, null)?.let { runCatching { RecordingMode.valueOf(it) }.getOrNull() }
      ?: RecordingMode.OFF
    set(value) = prefs.edit { putString(KEY_RECORDING, value.name) }

  enum class KeepRecordings {
    /** Until the user deletes them. */
    ALL,
    /** A new recording replaces the older ones that start at the same place. */
    REPLACE_SAME_PLACE,
  }

  var keepRecordings: KeepRecordings
    get() = prefs.getString(KEY_KEEP_RECORDINGS, null)?.let { runCatching { KeepRecordings.valueOf(it) }.getOrNull() }
      ?: KeepRecordings.ALL
    set(value) = prefs.edit { putString(KEY_KEEP_RECORDINGS, value.name) }

  var recordingSplit: RecordingSplit
    get() = prefs.getString(KEY_RECORDING_SPLIT, null)?.let { runCatching { RecordingSplit.valueOf(it) }.getOrNull() }
      ?: RecordingSplit.BY_SURA
    set(value) = prefs.edit { putString(KEY_RECORDING_SPLIT, value.name) }

  /** Where the user dragged the floating mic (its offset from the bottom corner), if they did. */
  var micButtonOffset: Pair<Float, Float>?
    get() = if (prefs.contains(KEY_MIC_X)) prefs.getFloat(KEY_MIC_X, 0f) to prefs.getFloat(KEY_MIC_Y, 0f) else null
    set(value) = prefs.edit {
      if (value == null) {
        remove(KEY_MIC_X)
        remove(KEY_MIC_Y)
      } else {
        putFloat(KEY_MIC_X, value.first)
        putFloat(KEY_MIC_Y, value.second)
      }
    }

  var disclaimerAccepted: Boolean
    get() = prefs.getBoolean(KEY_DISCLAIMER, false)
    set(value) = prefs.edit { putBoolean(KEY_DISCLAIMER, value) }

  fun hintConfig(salah: Boolean = false): HintConfig {
    val delayMs = hintDelaySeconds * 1_000L
    return HintConfig(
      delayMs = delayMs,
      wordsPerHint = wordsPerHint,
      onMistakes = correctorEnabled && hintOnMistakes,
      onPauses = correctorEnabled && hintOnPauses,
      // in salah a pause after an ayah is often ruku: give the takbir time to be recognized
      ayahBoundaryDelayMs = if (salah) 2 * delayMs else delayMs,
      // spoken hints shouldn't talk over the reciter (see HintConfig)
      mistakesWaitForPause = salah,
    )
  }

  private companion object {
    const val KEY_CORRECTOR = "corrector_enabled"
    const val KEY_DELAY = "hint_delay_seconds"
    const val KEY_WORDS = "words_per_hint"
    const val KEY_ON_MISTAKES = "hint_on_mistakes"
    const val KEY_ON_PAUSES = "hint_on_pauses"
    const val KEY_STRICT = "strict_tashkeel"
    const val KEY_DISCLAIMER = "disclaimer_accepted"
    const val KEY_PROMPT_QARI = "prompt_qari"
    const val KEY_AUTO_DOWNLOAD = "auto_download"
    const val KEY_RECORDING = "recording_mode"
    const val KEY_KEEP_RECORDINGS = "keep_recordings"
    const val KEY_RECORDING_SPLIT = "recording_split"
    const val KEY_MIC_X = "mic_button_x"
    const val KEY_MIC_Y = "mic_button_y"
  }
}
