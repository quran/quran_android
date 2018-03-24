package com.quran.data.page.provider

import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.source.PageProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
object QuranPageModule {

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("madani")
  fun provideMadaniPageSet(): PageProvider {
    return MadaniPageProvider()
  }
}
