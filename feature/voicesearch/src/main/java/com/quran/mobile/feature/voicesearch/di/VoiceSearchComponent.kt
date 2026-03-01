package com.quran.mobile.feature.voicesearch.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.voicesearch.VoiceSearchActivity
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface VoiceSearchComponent {
  fun inject(voiceSearchActivity: VoiceSearchActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): VoiceSearchComponent
  }
}
