package com.quran.mobile.feature.sync

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
      // The XML-defined categories are auto-ordered starting at 0 in document order; an explicit
      // negative order sorts Account above all of them, regardless of insertion order.
      order = -1
    }
    val preference = AccountPreference(context, syncManager).apply {
      key = PREFERENCE_KEY
    }
    root.addPreference(category)
    category.addPreference(preference)
  }

  // The account row handles its own clicks (open sync details, sign out) via Compose since it
  // is not selectable at the Preference level.
  override fun onPreferenceClick(preference: Preference): Boolean = false

  companion object {
    private const val CATEGORY_KEY = "quran_sync_settings_category"
    private const val PREFERENCE_KEY = "quran_sync_settings"
  }
}
