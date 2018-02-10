package com.quran.labs.androidquran.data

import com.quran.data.page.provider.QuranPageProvider
import com.quran.data.page.provider.size.QuranPageSizeCalculatorProvider
import com.quran.data.source.QuranDataSourceProvider
import dagger.Module
import dagger.Provides

@Module
object QuranDataModule {
  @JvmStatic @Provides fun provideQuranDataSource() =
      QuranDataSourceProvider.provideShemerlyDataSource()

  @JvmStatic @Provides fun provideQuranPageProvider() =
      QuranPageProvider.provideShemerlyPageProvider()

  @JvmStatic @Provides fun provideQuranPageSizeCalculator() =
      QuranPageSizeCalculatorProvider.provideShemerlyPageSizeCalculator()
}
