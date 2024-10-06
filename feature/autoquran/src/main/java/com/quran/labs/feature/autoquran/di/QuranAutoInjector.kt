package com.quran.labs.feature.autoquran.di

import com.quran.data.di.AppScope
import com.quran.labs.feature.autoquran.QuranAudioService
import com.squareup.anvil.annotations.ContributesTo

@ContributesTo(AppScope::class)
interface QuranAutoInjector {
  fun inject(service: QuranAudioService)
}
