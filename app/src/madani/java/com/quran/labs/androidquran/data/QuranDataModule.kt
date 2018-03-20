package com.quran.labs.androidquran.data

import android.view.Display
import com.quran.data.page.provider.common.QuranPageProvider
import com.quran.data.page.provider.common.size.QuranPageSizeCalculatorProvider
import dagger.Module
import dagger.Provides

@Module
object QuranDataModule {
  @JvmStatic @Provides fun provideQuranPageProvider() =
      QuranPageProvider.provideMadaniPageProvider()

  @JvmStatic @Provides fun provideQuranPageSizeCalculator(display: Display) =
      QuranPageSizeCalculatorProvider.provideMadaniPageSizeCalculator(display)
}
