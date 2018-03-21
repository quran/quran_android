package com.quran.data.page.provider.common

import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.page.provider.naskh.NaskhPageProvider
import com.quran.data.page.provider.shemerly.ShemerlyPageProvider
import com.quran.data.source.PageProvider

object QuranPageProvider {
  @JvmStatic fun provideMadaniPageProvider(): PageProvider = MadaniPageProvider()
  @JvmStatic fun provideNaskhPageProvider(): PageProvider = NaskhPageProvider()
  @JvmStatic fun provideQaloonPageProvider(): PageProvider = MadaniPageProvider()
  @JvmStatic fun provideShemerlyPageProvider(): PageProvider = ShemerlyPageProvider()
  @JvmStatic fun provideWarshPageProvider(): PageProvider = MadaniPageProvider()
}
