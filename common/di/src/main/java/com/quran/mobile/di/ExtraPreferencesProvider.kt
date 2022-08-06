package com.quran.mobile.di

import androidx.preference.Preference
import androidx.preference.PreferenceGroup

interface ExtraPreferencesProvider {

  /** The order (rank) this preference should show relative to other ExtraPreferences */
  val order: Int

  /** Callback to add the extra preferences to the supplied root preference screen */
  fun addPreferences(root: PreferenceGroup)

  /** Called when any preference in the tree is clicked. Return true if the click was handled */
  fun onPreferenceClick(preference: Preference): Boolean

}
