package com.quran.analytics.provider

import com.quran.analytics.AnalyticsProvider
import com.quran.analytics.CrashReporter
import dagger.Binds
import dagger.Module

@Module
interface AnalyticsModule {
  @Binds
  fun provideCrashReporter(noopCrashReporter: NoopCrashReporter): CrashReporter

  @Binds
  fun provideAnalyticsProvider(noopAnalyticsProvider: NoopAnalyticsProvider): AnalyticsProvider
}
