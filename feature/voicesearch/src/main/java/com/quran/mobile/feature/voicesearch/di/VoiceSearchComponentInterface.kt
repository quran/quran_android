package com.quran.mobile.feature.voicesearch.di

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface VoiceSearchComponentInterface {
  fun voiceSearchComponentFactory(): VoiceSearchComponent.Factory
}
