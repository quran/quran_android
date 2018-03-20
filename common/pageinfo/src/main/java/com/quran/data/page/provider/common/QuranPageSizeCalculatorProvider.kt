package com.quran.data.page.provider.common

import android.view.Display
import com.quran.data.page.provider.naskh.NaskhPageSizeCalculator
import com.quran.data.page.provider.shemerly.ShemerlyPageSizeCalculator

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
