package com.quran.data.di.impl

import com.quran.data.core.QuranFileManager
import com.quran.data.di.QuranPageDependencies
import javax.inject.Inject

class QuranPageDependenciesImpl @Inject constructor(
  private val quranFileManager: QuranFileManager
) : QuranPageDependencies {

  override fun provideQuranFileManager(): QuranFileManager = quranFileManager
}
