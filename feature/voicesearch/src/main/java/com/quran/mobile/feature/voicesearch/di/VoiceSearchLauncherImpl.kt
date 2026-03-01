package com.quran.mobile.feature.voicesearch.di

import android.content.Context
import android.content.Intent
import com.quran.data.di.AppScope
import com.quran.mobile.di.VoiceSearchLauncher
import com.quran.mobile.feature.voicesearch.VoiceSearchActivity
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class VoiceSearchLauncherImpl @Inject constructor(
  private val preferencesProvider: VoiceSearchPreferencesProvider
) : VoiceSearchLauncher {
  override val isEnabled: Boolean
    get() = preferencesProvider.isVoiceSearchEnabled()

  override fun createLaunchIntent(context: Context): Intent {
    return Intent(context, VoiceSearchActivity::class.java)
  }
}
