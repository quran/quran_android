package com.quran.data.page.provider

import android.view.Display
import com.quran.data.page.provider.impl.DefaultPageProvider
import com.quran.data.page.provider.impl.NaskhPageProvider
import com.quran.data.page.provider.impl.ShemerlyPageProvider

object QuranPageProvider {
  fun provideMadaniPageProvider(display: Display): PageProvider = DefaultPageProvider(display)
  fun provideNaskhPageProvider(display: Display): PageProvider = NaskhPageProvider(display)
  fun provideQaloonPageProvider(display: Display): PageProvider = DefaultPageProvider(display)
  fun provideShemerlyPageProvider(): PageProvider = ShemerlyPageProvider()
  fun provideWarshPageProvider(display: Display): PageProvider = DefaultPageProvider(display)
}
