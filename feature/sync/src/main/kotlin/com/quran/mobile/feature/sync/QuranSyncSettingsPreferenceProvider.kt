package com.quran.mobile.feature.sync

import android.content.Intent
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraPreferencesProvider
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class QuranSyncSettingsPreferenceProvider @Inject constructor(
  private val syncManager: QuranSyncManager
) : ExtraPreferencesProvider {
  override val order: Int = 0

  override fun addPreferences(root: PreferenceGroup) {
    if (!syncManager.isConfigured) {
      return
    }

    val context = root.context
    val category = PreferenceCategory(context).apply {
      key = CATEGORY_KEY
      title = context.getString(R.string.quran_sync_settings_category)
      isIconSpaceReserved = false
    }
    val preference = Preference(context).apply {
      key = PREFERENCE_KEY
      title = context.getString(R.string.quran_sync_title)
      summary = context.getString(R.string.quran_sync_settings_summary)
      isIconSpaceReserved = false
    }
    root.addPreference(category)
    category.addPreference(preference)
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    if (preference.key != PREFERENCE_KEY || !syncManager.isConfigured) {
      return false
    }
    val intent = Intent(preference.context, QuranSyncActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    preference.context.startActivity(intent)
    return true
  }

  companion object {
    private const val CATEGORY_KEY = "quran_sync_settings_category"
    private const val PREFERENCE_KEY = "quran_sync_settings"
  }
}
