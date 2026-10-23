package com.quran.mobile.feature.memorize.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraPreferencesProvider
import com.quran.mobile.feature.memorize.R
import com.quran.mobile.feature.memorize.check.DoubleCheckAdvice
import com.quran.mobile.feature.memorize.check.DoubleCheckModel
import com.quran.mobile.feature.memorize.model.ModelImportActivity
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.prompt.HintVoiceDownloads
import com.quran.mobile.feature.memorize.prompt.PromptPlayer
import com.quran.mobile.feature.memorize.recording.RecordingSplit
import com.quran.mobile.feature.memorize.recording.RecordingsActivity
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The "Memorize" section of the app's settings, including the prayer corrector. */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
@Inject
class MemorizePreferencesProvider(
  private val preferences: MemorizePreferences,
  private val models: SpeechModelStore,
  private val backgroundListening: BackgroundListening,
  private val promptPlayer: PromptPlayer,
  private val voiceDownloads: HintVoiceDownloads,
  private val doubleCheck: DoubleCheckModel,
  private val doubleCheckAdvice: DoubleCheckAdvice,
) : ExtraPreferencesProvider {

  override val order: Int = 10

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  // keeps the double-check's summary up to date (its download), for the settings screen shown last
  private var doubleCheckSummaryJob: Job? = null

  private val store = object : PreferenceDataStore() {
    override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
      KEY_CORRECTOR -> preferences.correctorEnabled
      KEY_ON_MISTAKES -> preferences.hintOnMistakes
      KEY_ON_PAUSES -> preferences.hintOnPauses
      KEY_STRICT -> preferences.strictTashkeel
      KEY_AUTO_DOWNLOAD -> preferences.autoDownload
      KEY_DOUBLE_CHECK -> preferences.doubleCheck
      else -> defValue
    }

    override fun putBoolean(key: String, value: Boolean) {
      when (key) {
        KEY_CORRECTOR -> preferences.correctorEnabled = value
        KEY_ON_MISTAKES -> preferences.hintOnMistakes = value
        KEY_ON_PAUSES -> preferences.hintOnPauses = value
        KEY_STRICT -> preferences.strictTashkeel = value
        KEY_AUTO_DOWNLOAD -> preferences.autoDownload = value
        KEY_DOUBLE_CHECK -> preferences.doubleCheck = value
      }
    }

    override fun getString(key: String, defValue: String?): String? = when (key) {
      KEY_DELAY -> preferences.hintDelaySeconds.toString()
      KEY_WORDS -> preferences.wordsPerHint.toString()
      KEY_VOICE -> preferences.promptQariId.toString()
      KEY_RECORDING -> preferences.recordingMode.name
      KEY_RECORDING_SPLIT -> preferences.recordingSplit.name
      KEY_KEEP_RECORDINGS -> preferences.keepRecordings.name
      else -> defValue
    }

    override fun putString(key: String, value: String?) {
      when (key) {
        KEY_RECORDING -> MemorizePreferences.RecordingMode.entries.firstOrNull { it.name == value }
          ?.let { preferences.recordingMode = it }
        KEY_RECORDING_SPLIT -> RecordingSplit.entries.firstOrNull { it.name == value }?.let { preferences.recordingSplit = it }
        KEY_KEEP_RECORDINGS -> MemorizePreferences.KeepRecordings.entries.firstOrNull { it.name == value }
          ?.let { preferences.keepRecordings = it }
      }
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
    category.addPreference(ListPreference(context).apply {
      setup(KEY_RECORDING, R.string.memorize_recording_title, null)
      entryValues = MemorizePreferences.RecordingMode.entries.map { it.name }.toTypedArray()
      entries = arrayOf(
        context.getString(R.string.memorize_recording_off),
        context.getString(R.string.memorize_recording_always),
        context.getString(R.string.memorize_recording_ask),
      )
      summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    })
    category.addPreference(ListPreference(context).apply {
      setup(KEY_RECORDING_SPLIT, R.string.memorize_recording_split_title, null)
      entryValues = RecordingSplit.entries.map { it.name }.toTypedArray()
      entries = arrayOf(
        context.getString(R.string.memorize_recording_split_sura),
        context.getString(R.string.memorize_recording_split_session),
      )
      summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    })
    category.addPreference(ListPreference(context).apply {
      setup(KEY_KEEP_RECORDINGS, R.string.memorize_keep_recordings_title, null)
      entryValues = MemorizePreferences.KeepRecordings.entries.map { it.name }.toTypedArray()
      entries = arrayOf(
        context.getString(R.string.memorize_keep_recordings_all),
        context.getString(R.string.memorize_keep_recordings_replace),
      )
      summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    })
    category.addPreference(Preference(context).apply {
      setup(KEY_RECORDINGS, R.string.memorize_recordings_title, R.string.memorize_recordings_summary)
    })
    category.addPreference(SwitchPreferenceCompat(context).apply {
      setup(KEY_DOUBLE_CHECK, R.string.memorize_double_check_title, null)
      setOnPreferenceChangeListener { preference, value ->
        onDoubleCheckChange(preference as SwitchPreferenceCompat, value as Boolean)
        // set once the user has answered
        false
      }
      doubleCheckSummaryJob?.cancel()
      doubleCheckSummaryJob = scope.launch {
        doubleCheck.store.state.collect { summary = doubleCheckSummary(context, it) }
      }
    })
    category.addPreference(SwitchPreferenceCompat(context).apply {
      setup(KEY_AUTO_DOWNLOAD, R.string.memorize_auto_download_title, R.string.memorize_auto_download_summary)
    })
    category.addPreference(Preference(context).apply {
      setup(KEY_OFFLINE, R.string.memorize_offline_title, R.string.memorize_offline_summary)
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
                models.spec.downloadSize / 1_000_000, models.spec.model.name, models.spec.tokens.name
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
      KEY_RECORDINGS -> {
        context.startActivity(Intent(context, RecordingsActivity::class.java))
        true
      }
      KEY_OFFLINE -> {
        offerOfflineDownload(context)
        true
      }
      else -> false
    }
  }

  private fun onDoubleCheckChange(preference: SwitchPreferenceCompat, on: Boolean) {
    val context = preference.context
    val size = Formatter.formatShortFileSize(context, DoubleCheckModel.SPEC.downloadSize)
    when {
      on && doubleCheck.isInstalled -> preference.isChecked = true
      on -> AlertDialog.Builder(context)
        .setTitle(R.string.memorize_double_check_title)
        .setMessage(
          context.getString(
            R.string.memorize_double_check_download_message,
            adviceText(context), size, DoubleCheckModel.SPEC.attribution, DoubleCheckModel.SPEC.license,
          )
        )
        .setPositiveButton(R.string.memorize_model_download) { _, _ ->
          preference.isChecked = true
          doubleCheck.store.startDownload()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
      else -> {
        preference.isChecked = false
        doubleCheck.store.cancelDownload()
        if (doubleCheck.isInstalled) {
          AlertDialog.Builder(context)
            .setTitle(R.string.memorize_double_check_title)
            .setMessage(context.getString(R.string.memorize_double_check_delete_message, size))
            .setPositiveButton(R.string.memorize_model_delete) { _, _ -> doubleCheck.delete() }
            .setNegativeButton(R.string.memorize_double_check_keep, null)
            .show()
        }
      }
    }
  }

  private fun doubleCheckSummary(context: Context, state: SpeechModelStore.State): String {
    val size = Formatter.formatShortFileSize(context, DoubleCheckModel.SPEC.downloadSize)
    val status = when {
      state is SpeechModelStore.State.Downloading && state.totalBytes > 0 ->
        context.getString(R.string.memorize_double_check_downloading, (100 * state.downloadedBytes / state.totalBytes).toInt())
      state is SpeechModelStore.State.Failed -> context.getString(R.string.memorize_model_failed, state.reason)
      preferences.doubleCheck && state !is SpeechModelStore.State.Installed ->
        context.getString(R.string.memorize_double_check_not_downloaded)
      else -> null
    }
    return listOfNotNull(context.getString(R.string.memorize_double_check_summary, size), adviceText(context), status)
      .joinToString(" ")
  }

  /** Whether the double-check suits this phone, and why. */
  private fun adviceText(context: Context): String {
    val advice = doubleCheckAdvice.advice()
    val seconds = String.format(java.util.Locale.getDefault(), "%.1f", advice.checkMs / 1000.0)
    return when (advice.reason) {
      DoubleCheckAdvice.Reason.SUITS -> context.getString(R.string.memorize_double_check_recommended)
      DoubleCheckAdvice.Reason.FAST -> context.getString(R.string.memorize_double_check_recommended_fast, seconds)
      DoubleCheckAdvice.Reason.LOW_MEMORY -> context.getString(
        R.string.memorize_double_check_low_memory, Formatter.formatShortFileSize(context, advice.memoryBytes),
      )
      DoubleCheckAdvice.Reason.OLD_PROCESSOR -> context.getString(R.string.memorize_double_check_old_processor)
      DoubleCheckAdvice.Reason.SLOW -> context.getString(R.string.memorize_double_check_slow, seconds)
    }
  }

  /** The speech model and the hint voice for the whole Quran, in one go. */
  private fun offerOfflineDownload(context: Context) {
    scope.launch {
      // reads the reciter's timings and files
      val voice = voiceDownloads.voice()
      val missing = withContext(Dispatchers.IO) { voiceDownloads.missing() }
      val voiceSize = withContext(Dispatchers.IO) { voiceDownloads.size(missing) }
      val modelMissing = !models.isInstalled()
      // the double-check where it is on, or where it suits the phone
      val offerCheck = !doubleCheck.isInstalled && (preferences.doubleCheck || doubleCheckAdvice.advice().recommended)
      val size = { bytes: Long -> Formatter.formatShortFileSize(context, bytes) }
      val lines = buildList {
        add(
          if (modelMissing) {
            context.getString(R.string.memorize_offline_model, size(models.spec.downloadSize))
          } else {
            context.getString(R.string.memorize_offline_model_done)
          }
        )
        if (offerCheck) {
          add(context.getString(R.string.memorize_offline_check, size(DoubleCheckModel.SPEC.downloadSize)))
        } else if (preferences.doubleCheck) {
          add(context.getString(R.string.memorize_offline_check_done))
        }
        if (voice != null) {
          val name = context.getString(voice.nameResource)
          add(
            when {
              missing.isEmpty() -> context.getString(R.string.memorize_offline_voice_done, name)
              voiceSize != null -> context.getString(R.string.memorize_offline_voice, name, 114 - missing.size, size(voiceSize))
              else -> context.getString(R.string.memorize_offline_voice_unknown, name, 114 - missing.size)
            }
          )
        }
      }
      val nothingToDo = !modelMissing && missing.isEmpty() && !offerCheck
      AlertDialog.Builder(context)
        .setTitle(R.string.memorize_offline_title)
        .setMessage(
          lines.joinToString("\n\n") + if (nothingToDo) "" else "\n\n" + context.getString(R.string.memorize_offline_wifi)
        )
        .apply {
          if (nothingToDo) {
            setPositiveButton(android.R.string.ok, null)
          } else {
            setPositiveButton(R.string.memorize_model_download) { _, _ ->
              if (modelMissing) models.startDownload()
              voiceDownloads.download(missing)
              if (offerCheck) {
                preferences.doubleCheck = true
                doubleCheck.store.startDownload()
              }
            }
            setNegativeButton(android.R.string.cancel, null)
          }
        }
        .show()
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
    const val KEY_AUTO_DOWNLOAD = "memorize_auto_download"
    const val KEY_DOUBLE_CHECK = "memorize_double_check"
    const val KEY_OFFLINE = "memorize_offline"
    const val KEY_RECORDING = "memorize_recording"
    const val KEY_RECORDING_SPLIT = "memorize_recording_split"
    const val KEY_KEEP_RECORDINGS = "memorize_keep_recordings"
    const val KEY_RECORDINGS = "memorize_recordings"
    const val KEY_PRIVACY = "memorize_privacy"
  }
}
