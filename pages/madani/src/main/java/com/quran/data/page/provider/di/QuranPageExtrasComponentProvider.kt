package com.quran.data.page.provider.di

interface QuranPageExtrasComponentProvider {
  fun provideQuranPageExtrasComponent(vararg pages: Int): QuranPageExtrasComponent
}
