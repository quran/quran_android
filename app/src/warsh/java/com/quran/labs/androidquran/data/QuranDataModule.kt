package com.quran.labs.androidquran.data

import android.view.Display
import com.quran.data.page.provider.common.QuranPageProvider
import com.quran.data.page.provider.common.size.QuranPageSizeCalculatorProvider
import com.quran.data.source.DisplaySize
import dagger.Module
import dagger.Provides

@Module
object QuranDataModule {
  @JvmStatic @Provides fun provideQuranPageProvider() =
      QuranPageProvider.provideWarshPageProvider()

  @JvmStatic @Provides fun provideQuranPageSizeCalculator(displaySize: DisplaySize) =
      QuranPageSizeCalculatorProvider.provideMadaniPageSizeCalculator(displaySize)
}
