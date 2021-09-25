package com.quran.data.di

import com.quran.data.core.QuranFileManager

interface QuranPageDependencies {
  fun provideQuranFileManager(): QuranFileManager
}

interface QuranPageDependenciesProvider {
  fun provideQuranPageDependencies(): QuranPageDependencies
}
