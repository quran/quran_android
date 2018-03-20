package com.quran.data.page.provider.common.size

import com.quran.data.page.provider.naskh.NaskhPageSizeCalculator
import com.quran.data.page.provider.shemerly.ShemerlyPageSizeCalculator
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageSizeCalculator

object QuranPageSizeCalculatorProvider {
  fun provideMadaniPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      DefaultPageSizeCalculator(displaySize)

  fun provideNaskhPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      NaskhPageSizeCalculator(displaySize)

  fun provideQaloonPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      NoOverridePageSizeCalculator(displaySize)

  fun provideShemerlyPageSizeCalculator(): PageSizeCalculator = ShemerlyPageSizeCalculator()

  fun provideWarshPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      NoOverridePageSizeCalculator(displaySize)
}
