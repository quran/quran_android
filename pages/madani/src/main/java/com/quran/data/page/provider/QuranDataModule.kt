package com.quran.data.page.provider

import com.quran.common.upgrade.LocalDataUpgrade
import com.quran.common.upgrade.PreferencesUpgrade
import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.data.pageinfo.mapper.IdentityAyahMapper
import com.quran.data.source.PageProvider
import com.quran.page.common.draw.ImageDrawHelper
import com.quran.page.common.factory.PageViewFactoryProvider
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
object QuranDataModule {

  @Provides
  fun providePageViewFactoryProvider(): PageViewFactoryProvider {
    return PageViewFactoryProvider { null }
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("madani")
  fun provideMadaniPageSet(): PageProvider {
    return MadaniPageProvider()
  }

  @JvmStatic
  @Provides
  @ElementsIntoSet
  fun provideImageDrawHelpers(): Set<ImageDrawHelper> {
    return emptySet()
  }

  @JvmStatic
  @Provides
  fun provideLocalDataUpgrade(): LocalDataUpgrade = object : LocalDataUpgrade {  }

  @JvmStatic
  @Provides
  fun providePreferencesUpgrade(): PreferencesUpgrade = PreferencesUpgrade { _, _, _ -> true }

  @JvmStatic
  @Reusable
  @Provides
  fun provideAyahMapper(): AyahMapper = IdentityAyahMapper()
}
