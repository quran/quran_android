package com.quran.mobile.feature.voicesearch.di

import android.content.Context
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraPreferencesProvider
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.voicesearch.R
import com.quran.mobile.feature.voicesearch.asr.AsrModelManager
import com.quran.mobile.feature.voicesearch.asr.ModelState
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class VoiceSearchPreferencesProvider @Inject constructor(
  @ApplicationContext private val appContext: Context,
  private val asrModelManager: AsrModelManager
) : ExtraPreferencesProvider {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var summaryJob: Job? = null

  override val order: Int = 50

  override fun addPreferences(root: PreferenceGroup) {
    val voiceSearchPref = CheckBoxPreference(root.context).apply {
      key = PREF_VOICE_SEARCH_ENABLED
      title = root.context.getString(R.string.voice_search_setting_title)
      isIconSpaceReserved = false
    }
    root.addPreference(voiceSearchPref)

    // Set initial summary based on model state
    asrModelManager.checkModelAvailability()
    updateSummary(voiceSearchPref)

    // Use WeakReference so the coroutine doesn't prevent GC of the preference/activity
    summaryJob?.cancel()
    val weakPref = WeakReference(voiceSearchPref)
    summaryJob = scope.launch {
      asrModelManager.modelState.collect {
        val pref = weakPref.get() ?: run {
          cancel()
          return@collect
        }
        updateSummary(pref)
      }
    }
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    if (preference.key == PREF_VOICE_SEARCH_ENABLED) {
      if (isVoiceSearchEnabled()) {
        asrModelManager.checkModelAvailability()
        if (!asrModelManager.isModelReady()) {
          scope.launch {
            asrModelManager.downloadModel()
          }
        }
      }
      updateSummary(preference as CheckBoxPreference)
      return true
    }
    return false
  }

  fun isVoiceSearchEnabled(): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(appContext)
      .getBoolean(PREF_VOICE_SEARCH_ENABLED, false)
  }

  private fun updateSummary(preference: CheckBoxPreference) {
    preference.summary = when (val state = asrModelManager.modelState.value) {
      is ModelState.NotDownloaded ->
        preference.context.getString(R.string.voice_search_setting_summary)
      is ModelState.Downloading ->
        preference.context.getString(R.string.voice_search_downloading_model) +
            " ${(state.progress * 100).toInt()}%"
      is ModelState.Ready -> if (isVoiceSearchEnabled()) {
        preference.context.getString(R.string.voice_search_setting_summary_ready)
      } else {
        preference.context.getString(R.string.voice_search_model_downloaded)
      }
      is ModelState.Error ->
        state.message
    }
  }

  companion object {
    const val PREF_VOICE_SEARCH_ENABLED = "pref_voice_search_enabled"
  }
}
