package com.quran.data.page.provider

import com.quran.common.upgrade.LocalDataUpgrade
import com.quran.common.upgrade.PreferencesUpgrade
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.page.provider.edition.Madani1421PageProvider
import com.quran.data.page.provider.edition.Madani1439LinesPageProvider
import com.quran.data.page.provider.edition.Madani1440PageProvider
import com.quran.data.page.provider.edition.Madani1441LinesPageProvider
import com.quran.data.page.provider.edition.ShemerlyPageProvider
import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.page.provider.tajweed.TajweedPageProvider
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.data.pageinfo.mapper.IdentityAyahMapper
import com.quran.data.source.PageProvider
import com.quran.page.common.draw.ImageDrawHelper
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey


@BindingContainer
object QuranDataModule {

  @Named(DependencyInjectionConstants.FALLBACK_PAGE_TYPE)
  @JvmStatic
  @Provides
  fun provideFallbackPageType(): String = "madani"

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("madani")
  fun provideMadaniPageSet(): PageProvider {
    return MadaniPageProvider()
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey(TajweedPageProvider.KEY)
  fun provideTajweedPageSet(): PageProvider = TajweedPageProvider()

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey(Madani1421PageProvider.KEY)
  fun provideMadani1421PageSet(): PageProvider = Madani1421PageProvider()

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey(Madani1440PageProvider.KEY)
  fun provideMadani1440PageSet(): PageProvider = Madani1440PageProvider()

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey(Madani1439LinesPageProvider.KEY)
  fun provideMadani1439LinesPageSet(): PageProvider = Madani1439LinesPageProvider()

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey(Madani1441LinesPageProvider.KEY)
  fun provideMadani1441LinesPageSet(): PageProvider = Madani1441LinesPageProvider()

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey(ShemerlyPageProvider.KEY)
  fun provideShemerlyPageSet(): PageProvider = ShemerlyPageProvider()

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
  @Provides
  fun provideAyahMapper(): AyahMapper = IdentityAyahMapper()
}
