package com.quran.data.page.provider.size

import android.view.Display
import com.quran.data.page.provider.size.impl.DefaultPageSizeCalculator
import com.quran.data.page.provider.size.impl.NaskhPageSizeCalculator
import com.quran.data.page.provider.size.impl.NoOverridePageSizeCalculator
import com.quran.data.page.provider.size.impl.ShemerlyPageSizeCalculator

object QuranPageSizeCalculatorProvider {
  fun provideMadaniPageSizeCalculator(display: Display): PageSizeCalculator =
      DefaultPageSizeCalculator(display)

  fun provideNaskhPageSizeCalculator(display: Display): PageSizeCalculator =
      NaskhPageSizeCalculator(display)

  fun provideQaloonPageSizeCalculator(display: Display): PageSizeCalculator =
      NoOverridePageSizeCalculator(display)

  fun provideShemerlyPageSizeCalculator(): PageSizeCalculator = ShemerlyPageSizeCalculator()

  fun provideWarshPageSizeCalculator(display: Display): PageSizeCalculator =
      NoOverridePageSizeCalculator(display)
}
