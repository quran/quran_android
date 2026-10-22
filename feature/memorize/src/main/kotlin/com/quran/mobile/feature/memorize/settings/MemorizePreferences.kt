package com.quran.mobile.feature.memorize.settings

import android.content.Context
import androidx.core.content.edit
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
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
  }
}
