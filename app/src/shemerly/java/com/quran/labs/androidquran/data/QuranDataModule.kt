package com.quran.labs.androidquran.data

import com.quran.data.page.provider.common.QuranPageProvider
import com.quran.data.page.provider.common.size.QuranPageSizeCalculatorProvider
import dagger.Module
import dagger.Provides

@Module
object QuranDataModule {
  @JvmStatic @Provides fun provideQuranPageProvider() =
      QuranPageProvider.provideShemerlyPageProvider()

  @JvmStatic @Provides fun provideQuranPageSizeCalculator() =
      QuranPageSizeCalculatorProvider.provideShemerlyPageSizeCalculator()
}
