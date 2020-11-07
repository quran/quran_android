package com.quran.labs.androidquran.di.component.application;

import com.quran.analytics.provider.AnalyticsModule;
import com.quran.common.networking.NetworkModule;
import com.quran.data.page.provider.QuranPageModule;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.QuranDataActivity;
import com.quran.labs.androidquran.QuranForwarderActivity;
import com.quran.labs.androidquran.QuranImportActivity;
import com.quran.labs.androidquran.SearchActivity;
import com.quran.labs.androidquran.core.worker.di.WorkerModule;
import com.quran.labs.androidquran.data.QuranDataModule;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.di.component.activity.PagerActivityComponent;
import com.quran.labs.androidquran.di.component.activity.QuranActivityComponent;
import com.quran.labs.androidquran.di.module.application.ApplicationModule;
import com.quran.labs.androidquran.di.module.application.DatabaseModule;
import com.quran.labs.androidquran.di.module.widgets.BookmarksWidgetUpdaterModule;
import com.quran.labs.androidquran.pageselect.PageSelectActivity;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.ui.AudioManagerActivity;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.SheikhAudioManagerActivity;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.AyahPlaybackFragment;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;
import com.quran.labs.androidquran.ui.fragment.JumpFragment;
import com.quran.labs.androidquran.ui.fragment.JuzListFragment;
import com.quran.labs.androidquran.ui.fragment.QuranAdvancedSettingsFragment;
import com.quran.labs.androidquran.ui.fragment.QuranSettingsFragment;
import com.quran.labs.androidquran.ui.fragment.SuraListFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.widget.BookmarksWidget;
import com.quran.labs.androidquran.widget.ShowJumpFragmentActivity;
import com.quran.labs.androidquran.widget.BookmarksWidgetListProvider;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
    AnalyticsModule.class,
    ApplicationModule.class,
    DatabaseModule.class,
    NetworkModule.class,
    QuranDataModule.class,
    QuranPageModule.class,
    WorkerModule.class,
    BookmarksWidgetUpdaterModule.class
} )
public interface ApplicationComponent {
  // subcomponents
  PagerActivityComponent.Builder pagerActivityComponentBuilder();
  QuranActivityComponent.Builder quranActivityComponentBuilder();

  // application
  void inject(QuranApplication quranApplication);

  // content provider
  void inject(QuranDataProvider quranDataProvider);

  // services
  void inject(AudioService audioService);
  void inject(QuranDownloadService quranDownloadService);

  // activities
  void inject(QuranDataActivity quranDataActivity);
  void inject(QuranImportActivity quranImportActivity);
  void inject(AudioManagerActivity audioManagerActivity);
  void inject(SheikhAudioManagerActivity sheikhAudioManagerActivity);
  void inject(QuranForwarderActivity quranForwarderActivity);
  void inject(SearchActivity searchActivity);
  void inject(PageSelectActivity pageSelectActivity);
  void inject(ShowJumpFragmentActivity showJumpFragmentActivity);

  // fragments
  void inject(BookmarksFragment bookmarksFragment);
  void inject(QuranSettingsFragment fragment);
  void inject(TranslationManagerActivity translationManagerActivity);
  void inject(QuranAdvancedSettingsFragment quranAdvancedSettingsFragment);
  void inject(SuraListFragment suraListFragment);
  void inject(JuzListFragment juzListFragment);
  void inject(AyahPlaybackFragment ayahPlaybackFragment);
  void inject(JumpFragment jumpFragment);

  // dialogs
  void inject(TagBookmarkDialog tagBookmarkDialog);
  void inject(AddTagDialog addTagDialog);

  // widgets
  void inject(BookmarksWidgetListProvider bookmarksWidgetListProvider);
  void inject(BookmarksWidget bookmarksWidget);
}
