package com.quran.labs.androidquran.di

import com.quran.analytics.provider.AnalyticsModule
import com.quran.common.networking.NetworkModule
import com.quran.data.di.AppScope
import com.quran.data.page.provider.QuranDataModule
import com.quran.labs.androidquran.core.worker.di.WorkerModule
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.di.module.application.ApplicationModule
import com.quran.labs.androidquran.di.module.application.DatabaseModule
import com.quran.labs.androidquran.di.module.application.PageAggregationModule
import com.quran.labs.androidquran.di.module.widgets.BookmarksWidgetUpdaterModule
import com.quran.labs.androidquran.di.quran.TestQuranActivityComponent
import com.squareup.anvil.annotations.MergeComponent
import javax.inject.Singleton

@Singleton
@MergeComponent(
  AppScope::class,
  modules = [
    AnalyticsModule::class,
    ApplicationModule::class,
    DatabaseModule::class,
    NetworkModule::class,
    PageAggregationModule::class,
    QuranDataModule::class,
    WorkerModule::class,
    BookmarksWidgetUpdaterModule::class
  ]
)
interface TestApplicationComponent : ApplicationComponent {
  override fun quranActivityComponentBuilder(): TestQuranActivityComponent.Builder
}
