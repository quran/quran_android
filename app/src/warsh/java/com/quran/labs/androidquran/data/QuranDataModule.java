package com.quran.labs.androidquran.data;

import com.quran.data.source.PageProvider;
import com.quran.data.source.QuranDataSource;
import dagger.Module;
import dagger.Provides;
import java.util.Map;

@Module
public class QuranDataModule {

  @Provides
  static PageProvider provideQuranPageProvider(Map<String, PageProvider> providers) {
    return providers.get("warsh");
  }

  @Provides
  static QuranDataSource provideQuranDataSource(PageProvider pageProvider) {
    return pageProvider.getDataSource();
  }
}
