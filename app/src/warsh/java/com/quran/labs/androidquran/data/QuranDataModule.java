package com.quran.labs.androidquran.data;

import com.quran.data.page.provider.common.QuranPageProvider;
import com.quran.data.source.PageProvider;

import dagger.Module;
import dagger.Provides;

@Module
public abstract class QuranDataModule {

  @Provides
  public static PageProvider provideQuranPageProvider() {
    return QuranPageProvider.provideWarshPageProvider();
  }
}
