package com.quran.labs.androidquran.data

import android.view.Display
import com.quran.data.page.provider.QuranPageProvider
import com.quran.data.source.QuranDataSourceProvider
import dagger.Module
import dagger.Provides

@Module
object QuranDataModule {
  @JvmStatic @Provides fun provideQuranDataSource() =
      QuranDataSourceProvider.provideMadaniDataSource()

  @Deprecated(message = "Use standard DI")
  @JvmStatic fun legacyProvideQuranPageProvider(display: Display) =
      QuranPageProvider.provideMadaniPageProvider(display)

  @JvmStatic @Provides fun provideQuranPageProvider(display: Display) =
      QuranPageProvider.provideMadaniPageProvider(display)
}
