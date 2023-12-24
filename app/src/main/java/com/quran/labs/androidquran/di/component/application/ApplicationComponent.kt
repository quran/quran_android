package com.quran.labs.androidquran.di.component.application

import android.content.Context
import com.quran.analytics.provider.AnalyticsModule
import com.quran.common.networking.NetworkModule
import com.quran.data.di.AppScope
import com.quran.data.page.provider.QuranDataModule
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.QuranForwarderActivity
import com.quran.labs.androidquran.QuranImportActivity
import com.quran.labs.androidquran.SearchActivity
import com.quran.labs.androidquran.core.worker.di.WorkerModule
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.di.component.activity.PagerActivityComponent
import com.quran.labs.androidquran.di.component.activity.QuranActivityComponent
import com.quran.labs.androidquran.di.module.application.ApplicationModule
import com.quran.labs.androidquran.di.module.application.DatabaseModule
import com.quran.labs.androidquran.di.module.application.PageAggregationModule
import com.quran.labs.androidquran.di.module.widgets.BookmarksWidgetUpdaterModule
import com.quran.labs.androidquran.pageselect.PageSelectActivity
import com.quran.labs.androidquran.service.AudioService
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import com.quran.labs.androidquran.ui.fragment.AddTagDialog
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment
import com.quran.labs.androidquran.ui.fragment.JumpFragment
import com.quran.labs.androidquran.ui.fragment.JuzListFragment
import com.quran.labs.androidquran.ui.fragment.QuranAdvancedSettingsFragment
import com.quran.labs.androidquran.ui.fragment.QuranSettingsFragment
import com.quran.labs.androidquran.ui.fragment.SuraListFragment
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog
import com.quran.labs.androidquran.widget.BookmarksWidget
import com.quran.labs.androidquran.widget.BookmarksWidgetListProvider
import com.quran.labs.androidquran.widget.ShowJumpFragmentActivity
import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.qualifier.ApplicationContext
import com.squareup.anvil.annotations.MergeComponent
import dagger.BindsInstance
import dagger.Component
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
interface ApplicationComponent: QuranApplicationComponent {
  // subcomponents
  fun pagerActivityComponentFactory(): PagerActivityComponent.Factory
  fun quranActivityComponentFactory(): QuranActivityComponent.Factory

  // application
  fun inject(quranApplication: QuranApplication)

  // content provider
  fun inject(quranDataProvider: QuranDataProvider)

  // services
  fun inject(audioService: AudioService)
  fun inject(quranDownloadService: QuranDownloadService)

  // activities
  fun inject(quranDataActivity: QuranDataActivity)
  fun inject(quranImportActivity: QuranImportActivity)
  fun inject(quranForwarderActivity: QuranForwarderActivity)
  fun inject(searchActivity: SearchActivity)
  fun inject(pageSelectActivity: PageSelectActivity)
  fun inject(showJumpFragmentActivity: ShowJumpFragmentActivity)

  // fragments
  fun inject(bookmarksFragment: BookmarksFragment)
  fun inject(fragment: QuranSettingsFragment)
  fun inject(translationManagerActivity: TranslationManagerActivity)
  fun inject(quranAdvancedSettingsFragment: QuranAdvancedSettingsFragment)
  fun inject(suraListFragment: SuraListFragment)
  fun inject(juzListFragment: JuzListFragment)
  fun inject(jumpFragment: JumpFragment)

  // dialogs
  fun inject(tagBookmarkDialog: TagBookmarkDialog)
  fun inject(addTagDialog: AddTagDialog)

  // widgets
  fun inject(bookmarksWidgetListProvider: BookmarksWidgetListProvider)
  fun inject(bookmarksWidget: BookmarksWidget)

  @Component.Factory
  interface Factory {
    fun generate(@BindsInstance @ApplicationContext appContext: Context): ApplicationComponent
  }
}
