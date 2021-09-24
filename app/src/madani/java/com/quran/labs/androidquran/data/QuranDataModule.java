package com.quran.labs.androidquran.data;

import com.quran.data.source.PageProvider;
import com.quran.data.source.QuranDataSource;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.page.common.factory.PageViewFactoryProvider;

import java.util.Map;

import dagger.Module;
import dagger.Provides;

@Module
public class QuranDataModule {

  @Provides
  static PageProvider provideQuranPageProvider(Map<String, PageProvider> providers,
                                               QuranSettings quranSettings) {
    final String key = quranSettings.getPageType();
    final String fallbackType = "madani";
    if (key == null) {
      quranSettings.setPageType(fallbackType);
    }
    return providers.get(key == null ? fallbackType : key);
  }

  @Provides
  static PageViewFactoryProvider providePageViewFactoryProvider() {
    return pageType -> null;
  }

  @Provides
  static QuranDataSource provideQuranDataSource(PageProvider pageProvider) {
    return pageProvider.getDataSource();
  }
}
