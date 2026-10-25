package com.quran.labs.androidquran.appicon

import com.quran.data.di.AppScope
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.feature.appicon.AppIconPreference
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

// per installation, since the launcher alias it mirrors isn't backed up either
@ContributesBinding(AppScope::class)
@Inject
class QuranAppIconPreference(private val quranSettings: QuranSettings) : AppIconPreference {
  override var savedIconId: String?
    get() = quranSettings.savedAppIcon
    set(value) {
      quranSettings.savedAppIcon = value
    }

  override var hasPendingIcon: Boolean
    get() = quranSettings.isAppIconPending
    set(value) {
      quranSettings.isAppIconPending = value
    }

  override var savedAvailability: Boolean?
    get() = quranSettings.appIconsAvailable
    set(value) {
      quranSettings.appIconsAvailable = value
    }
}
