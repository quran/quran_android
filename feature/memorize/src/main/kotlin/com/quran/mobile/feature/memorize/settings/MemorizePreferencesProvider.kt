package com.quran.mobile.feature.memorize.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraPreferencesProvider
import com.quran.mobile.feature.memorize.R
import com.quran.mobile.feature.memorize.model.ModelImportActivity
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.prompt.PromptPlayer
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** The "Memorize" section of the app's settings, including the prayer corrector. */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
@Inject
class MemorizePreferencesProvider(
  private val preferences: MemorizePreferences,
  private val models: SpeechModelStore,
  private val backgroundListening: BackgroundListening,
  private val promptPlayer: PromptPlayer,
) : ExtraPreferencesProvider {

  override val order: Int = 10

  private val store = object : PreferenceDataStore() {
    override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
      KEY_CORRECTOR -> preferences.correctorEnabled
      KEY_ON_MISTAKES -> preferences.hintOnMistakes
      KEY_ON_PAUSES -> preferences.hintOnPauses
      KEY_STRICT -> preferences.strictTashkeel
      else -> defValue
    }

    override fun putBoolean(key: String, value: Boolean) {
      when (key) {
        KEY_CORRECTOR -> preferences.correctorEnabled = value
        KEY_ON_MISTAKES -> preferences.hintOnMistakes = value
        KEY_ON_PAUSES -> preferences.hintOnPauses = value
        KEY_STRICT -> preferences.strictTashkeel = value
      }
    }

    override fun getString(key: String, defValue: String?): String? = when (key) {
      KEY_DELAY -> preferences.hintDelaySeconds.toString()
      KEY_WORDS -> preferences.wordsPerHint.toString()
      KEY_VOICE -> preferences.promptQariId.toString()
      else -> defValue
    }

    override fun putString(key: String, value: String?) {
      val number = value?.toIntOrNull() ?: return
      when (key) {
        KEY_DELAY -> preferences.hintDelaySeconds = number
        KEY_WORDS -> preferences.wordsPerHint = number
        KEY_VOICE -> preferences.promptQariId = number
      }
    }
  }

  override fun addPreferences(root: PreferenceGroup) {
    val context = root.context
    val category = PreferenceCategory(context).apply {
      key = KEY_CATEGORY
      title = context.getString(R.string.memorize_settings_category)
      isIconSpaceReserved = false
    }
    root.addPreference(category)

    category.addPreference(SwitchPreferenceCompat(context).apply {
      setup(KEY_CORRECTOR, R.string.memorize_corrector_title, R.string.memorize_corrector_summary)
    })
    category.addPreference(ListPreference(context).apply {
      setup(KEY_DELAY, R.string.memorize_delay_title, null)
      entryValues = arrayOf("2", "3", "4", "5", "7", "10", "15")
      entries = entryValues.map {
        context.resources.getQuantityString(R.plurals.memorize_seconds, it.toString().toInt(), it.toString().toInt())
      }.toTypedArray()
      summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    })
    category.addPreference(ListPreference(context).apply {
      setup(KEY_WORDS, R.string.memorize_words_title, null)
      entryValues = arrayOf("1", "2", "3", "5")
      entries = entryValues.map {
        context.resources.getQuantityString(R.plurals.memorize_words, it.toString().toInt(), it.toString().toInt())
      }.toTypedArray()
      summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    })
    category.addPreference(SwitchPreferenceCompat(context).apply {
      setup(KEY_ON_MISTAKES, R.string.memorize_on_mistakes_title, R.string.memorize_on_mistakes_summary)
    })
    category.addPreference(SwitchPreferenceCompat(context).apply {
      setup(KEY_ON_PAUSES, R.string.memorize_on_pauses_title, R.string.memorize_on_pauses_summary)
    })
    val voices = promptPlayer.availableVoices()
    if (voices.isNotEmpty()) {
      category.addPreference(ListPreference(context).apply {
        setup(KEY_VOICE, R.string.memorize_voice_title, null)
        entryValues = voices.map { it.toString() }.toTypedArray()
        entries = voices.map { id -> promptPlayer.voiceName(id)?.let { context.getString(it) } ?: id.toString() }
          .toTypedArray()
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
      })
    }
    category.addPreference(SwitchPreferenceCompat(context).apply {
      setup(KEY_STRICT, R.string.memorize_strict_title, R.string.memorize_strict_summary)
    })
    category.addPreference(Preference(context).apply {
      setup(KEY_BACKGROUND, R.string.memorize_background_title, R.string.memorize_background_summary)
    })
    category.addPreference(Preference(context).apply {
      setup(KEY_MODEL, R.string.memorize_model_setting_title, null)
      summary = modelSummary(context)
    })
    category.addPreference(Preference(context).apply {
      setup(KEY_PRIVACY, R.string.memorize_privacy_title, R.string.memorize_privacy_summary)
      isSelectable = false
    })
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    return when (preference.key) {
      KEY_MODEL -> {
        if (models.isInstalled()) {
          AlertDialog.Builder(context)
            .setTitle(R.string.memorize_model_setting_title)
            .setMessage(context.getString(R.string.memorize_model_delete_message, models.spec.attribution, models.spec.license))
            .setPositiveButton(R.string.memorize_model_delete) { _, _ ->
              models.delete()
              preference.summary = modelSummary(context)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        } else {
          AlertDialog.Builder(context)
            .setTitle(R.string.memorize_model_setting_title)
            .setMessage(
              context.getString(
                R.string.memorize_model_missing_message,
                models.spec.files.sumOf { it.size } / 1_000_000, models.spec.model.name, models.spec.tokens.name
              )
            )
            .setPositiveButton(R.string.memorize_model_import) { _, _ ->
              context.startActivity(Intent(context, ModelImportActivity::class.java))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        }
        true
      }
      KEY_BACKGROUND -> {
        backgroundListening.showSetup(context)
        true
      }
      else -> false
    }
  }

  private fun modelSummary(context: Context): String =
    if (models.isInstalled()) {
      context.getString(R.string.memorize_model_installed, models.spec.attribution)
    } else {
      context.getString(R.string.memorize_model_not_installed)
    }

  private fun Preference.setup(key: String, title: Int, summary: Int?) {
    this.key = key
    this.preferenceDataStore = store
    this.isIconSpaceReserved = false
    this.isPersistent = true
    setTitle(title)
    if (summary != null) setSummary(summary)
  }

  private companion object {
    const val KEY_CATEGORY = "memorize_category"
    const val KEY_CORRECTOR = "memorize_corrector"
    const val KEY_DELAY = "memorize_delay"
    const val KEY_WORDS = "memorize_words"
    const val KEY_ON_MISTAKES = "memorize_on_mistakes"
    const val KEY_ON_PAUSES = "memorize_on_pauses"
    const val KEY_VOICE = "memorize_voice"
    const val KEY_STRICT = "memorize_strict"
    const val KEY_BACKGROUND = "memorize_background"
    const val KEY_MODEL = "memorize_model"
    const val KEY_PRIVACY = "memorize_privacy"
  }
}
