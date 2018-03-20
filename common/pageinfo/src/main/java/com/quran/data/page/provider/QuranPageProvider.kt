package com.quran.data.page.provider

import com.quran.data.page.provider.common.DefaultPageProvider
import com.quran.data.page.provider.naskh.NaskhPageProvider
import com.quran.data.page.provider.shemerly.ShemerlyPageProvider
import com.quran.data.source.PageProvider

object QuranPageProvider {
  fun provideMadaniPageProvider(): PageProvider = DefaultPageProvider()
  fun provideNaskhPageProvider(): PageProvider = NaskhPageProvider()
  fun provideQaloonPageProvider(): PageProvider = DefaultPageProvider()
  fun provideShemerlyPageProvider(): PageProvider = ShemerlyPageProvider()
  fun provideWarshPageProvider(): PageProvider = DefaultPageProvider()
}
