package com.quran.labs.androidquran.data

import android.view.Display
import com.quran.data.page.provider.QuranPageProvider
import com.quran.data.page.provider.common.QuranPageSizeCalculatorProvider
import dagger.Module
import dagger.Provides

@Module
object QuranDataModule {
  @JvmStatic @Provides fun provideQuranPageProvider() =
      QuranPageProvider.provideNaskhPageProvider()

  @JvmStatic @Provides fun provideQuranPageSizeCalculator(display: Display) =
      QuranPageSizeCalculatorProvider.provideNaskhPageSizeCalculator(display)
}
