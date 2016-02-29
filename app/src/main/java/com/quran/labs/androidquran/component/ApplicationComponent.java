package com.quran.labs.androidquran.component;

import com.quran.labs.androidquran.QuranImportActivity;
import com.quran.labs.androidquran.module.ApplicationModule;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.TranslationManagerActivity;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment;
import com.quran.labs.androidquran.ui.fragment.QuranSettingsFragment;
import com.quran.labs.androidquran.ui.fragment.TabletFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.util.QuranPageTask;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = ApplicationModule.class)
public interface ApplicationComponent {
  // services
  void inject(QuranDownloadService quranDownloadService);

  // activities
  void inject(QuranActivity quranActivity);
  void inject(PagerActivity pagerActivity);
  void inject(QuranImportActivity quranImportActivity);

  // fragments
  void inject(QuranPageFragment quranPageFragment);
  void inject(TabletFragment tabletFragment);
  void inject(BookmarksFragment bookmarksFragment);
  void inject(QuranSettingsFragment fragment);
  void inject(TranslationManagerActivity translationManagerActivity);

  // dialogs
  void inject(TagBookmarkDialog tagBookmarkDialog);
  void inject(AddTagDialog addTagDialog);

  // misc
  void inject(QuranPageTask quranPageTask);
}
