package com.quran.data.page.provider.common

import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.page.provider.naskh.NaskhPageProvider
import com.quran.data.page.provider.shemerly.ShemerlyPageProvider
import com.quran.data.source.PageProvider

object QuranPageProvider {
  fun provideMadaniPageProvider(): PageProvider = MadaniPageProvider()
  fun provideNaskhPageProvider(): PageProvider = NaskhPageProvider()
  fun provideQaloonPageProvider(): PageProvider = MadaniPageProvider()
  fun provideShemerlyPageProvider(): PageProvider = ShemerlyPageProvider()
  fun provideWarshPageProvider(): PageProvider = MadaniPageProvider()
}
