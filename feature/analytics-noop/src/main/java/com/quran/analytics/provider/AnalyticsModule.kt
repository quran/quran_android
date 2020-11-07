package com.quran.analytics.provider

import com.quran.analytics.AnalyticsProvider
import dagger.Binds
import dagger.Module

@Module
interface AnalyticsModule {

  @Binds
  fun provideAnalyticsProvider(noopAnalyticsProvider: NoopAnalyticsProvider): AnalyticsProvider
}
