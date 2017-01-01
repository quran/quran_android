package com.quran.labs.androidquran.component.application;

import com.quran.labs.androidquran.QuranImportActivity;
import com.quran.labs.androidquran.component.activity.PagerActivityComponent;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.module.application.ApplicationModule;
import com.quran.labs.androidquran.module.application.DatabaseModule;
import com.quran.labs.androidquran.module.application.NetworkModule;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;
import com.quran.labs.androidquran.ui.fragment.QuranAdvancedSettingsFragment;
import com.quran.labs.androidquran.ui.fragment.QuranSettingsFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = { ApplicationModule.class, DatabaseModule.class, NetworkModule.class } )
public interface ApplicationComponent {
  // subcomponents
  PagerActivityComponent.Builder pagerActivityComponentBuilder();

  // content provider
  void inject(QuranDataProvider quranDataProvider);

  // services
  void inject(QuranDownloadService quranDownloadService);

  // activities
  void inject(QuranActivity quranActivity);
  void inject(QuranImportActivity quranImportActivity);

  // fragments
  void inject(BookmarksFragment bookmarksFragment);
  void inject(QuranSettingsFragment fragment);
  void inject(TranslationManagerActivity translationManagerActivity);
  void inject(QuranAdvancedSettingsFragment quranAdvancedSettingsFragment);

  // dialogs
  void inject(TagBookmarkDialog tagBookmarkDialog);
  void inject(AddTagDialog addTagDialog);
}
