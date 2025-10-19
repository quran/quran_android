package com.quran.analytics.provider

import com.quran.analytics.AnalyticsProvider
import com.quran.analytics.CrashReporter
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds

@BindingContainer
interface AnalyticsModule {
  @Binds val NoopCrashReporter.bind: CrashReporter
  @Binds val NoopAnalyticsProvider.bind: AnalyticsProvider
}
