package com.quran.data.page.provider

import com.quran.data.page.provider.impl.DefaultPageProvider
import com.quran.data.page.provider.impl.NaskhPageProvider
import com.quran.data.page.provider.impl.ShemerlyPageProvider

object QuranPageProvider {
  fun provideMadaniPageProvider(): PageProvider = DefaultPageProvider()
  fun provideNaskhPageProvider(): PageProvider = NaskhPageProvider()
  fun provideQaloonPageProvider(): PageProvider = DefaultPageProvider()
  fun provideShemerlyPageProvider(): PageProvider = ShemerlyPageProvider()
  fun provideWarshPageProvider(): PageProvider = DefaultPageProvider()
}
