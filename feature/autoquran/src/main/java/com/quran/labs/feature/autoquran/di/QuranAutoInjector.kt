package com.quran.labs.feature.autoquran.di

import com.quran.data.di.AppScope
import com.quran.labs.feature.autoquran.service.QuranBrowsableAudioPlaybackService
import com.squareup.anvil.annotations.ContributesTo

@ContributesTo(AppScope::class)
interface QuranAutoInjector {
  fun inject(service: QuranBrowsableAudioPlaybackService)
}
