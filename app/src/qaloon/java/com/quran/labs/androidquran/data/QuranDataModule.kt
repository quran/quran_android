package com.quran.labs.androidquran.data

import com.quran.data.page.provider.common.QuranPageProvider
import dagger.Module
import dagger.Provides

@Module
object QuranDataModule {
  @JvmStatic @Provides fun provideQuranPageProvider() =
      QuranPageProvider.provideQaloonPageProvider()
}
