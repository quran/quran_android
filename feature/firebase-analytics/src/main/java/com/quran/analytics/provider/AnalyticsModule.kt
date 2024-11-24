package com.quran.analytics.provider

import com.quran.analytics.AnalyticsProvider
import com.quran.analytics.CrashReporter
import dagger.Binds
import dagger.Module

@Module
interface AnalyticsModule {

  @Binds
  fun provideCrashReporter(crashReporter: FirebaseCrashReporter): CrashReporter

  @Binds
  fun provideAnalyticsProvider(firebaseAnalyticsProvider: FirebaseProvider): AnalyticsProvider
}
