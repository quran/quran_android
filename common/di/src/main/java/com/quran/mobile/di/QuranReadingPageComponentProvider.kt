package com.quran.mobile.di

interface QuranReadingPageComponentProvider {
  fun provideQuranReadingPageComponent(vararg pages: Int): QuranReadingPageComponent
}
