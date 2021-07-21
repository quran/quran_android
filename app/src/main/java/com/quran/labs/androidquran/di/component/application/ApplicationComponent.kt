package com.quran.labs.androidquran.di.component.application

import com.quran.analytics.provider.AnalyticsModule
import com.quran.common.networking.NetworkModule
import com.quran.data.page.provider.QuranPageModule
import com.quran.labs.androidquran.*
import com.quran.labs.androidquran.core.worker.di.WorkerModule
import com.quran.labs.androidquran.data.QuranDataModule
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.di.component.activity.PagerActivityComponent
import com.quran.labs.androidquran.di.component.activity.QuranActivityComponent
import com.quran.labs.androidquran.di.module.application.ApplicationModule
import com.quran.labs.androidquran.di.module.application.DatabaseModule
import com.quran.labs.androidquran.di.module.widgets.BookmarksWidgetUpdaterModule
import com.quran.labs.androidquran.pageselect.PageSelectActivity
import com.quran.labs.androidquran.service.AudioService
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.ui.AudioManagerActivity
import com.quran.labs.androidquran.ui.SheikhAudioManagerActivity
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import com.quran.labs.androidquran.ui.fragment.*
import com.quran.labs.androidquran.widget.BookmarksWidget
import com.quran.labs.androidquran.widget.BookmarksWidgetListProvider
import com.quran.labs.androidquran.widget.ShowJumpFragmentActivity
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
  modules = [
    AnalyticsModule::class,
    ApplicationModule::class,
    DatabaseModule::class,
    NetworkModule::class,
    QuranDataModule::class,
    QuranPageModule::class,
    WorkerModule::class,
    BookmarksWidgetUpdaterModule::class
  ]
)
interface ApplicationComponent {
  // subcomponents
  fun pagerActivityComponentBuilder(): PagerActivityComponent.Builder
  fun quranActivityComponentBuilder(): QuranActivityComponent.Builder

  // application
  fun inject(quranApplication: QuranApplication?)

  // content provider
  fun inject(quranDataProvider: QuranDataProvider?)

  // services
  fun inject(audioService: AudioService?)
  fun inject(quranDownloadService: QuranDownloadService?)

  // activities
  fun inject(quranDataActivity: QuranDataActivity?)
  fun inject(quranImportActivity: QuranImportActivity?)
  fun inject(audioManagerActivity: AudioManagerActivity?)
  fun inject(sheikhAudioManagerActivity: SheikhAudioManagerActivity?)
  fun inject(quranForwarderActivity: QuranForwarderActivity?)
  fun inject(searchActivity: SearchActivity?)
  fun inject(pageSelectActivity: PageSelectActivity?)
  fun inject(showJumpFragmentActivity: ShowJumpFragmentActivity?)

  // fragments
  fun inject(bookmarksFragment: BookmarksFragment?)
  fun inject(fragment: QuranSettingsFragment?)
  fun inject(translationManagerActivity: TranslationManagerActivity?)
  fun inject(quranAdvancedSettingsFragment: QuranAdvancedSettingsFragment?)
  fun inject(suraListFragment: SuraListFragment?)
  fun inject(juzListFragment: JuzListFragment?)
  fun inject(ayahPlaybackFragment: AyahPlaybackFragment?)
  fun inject(jumpFragment: JumpFragment?)

  // dialogs
  fun inject(tagBookmarkDialog: TagBookmarkDialog?)
  fun inject(addTagDialog: AddTagDialog?)

  // widgets
  fun inject(bookmarksWidgetListProvider: BookmarksWidgetListProvider?)
  fun inject(bookmarksWidget: BookmarksWidget?)
}
